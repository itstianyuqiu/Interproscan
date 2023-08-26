package com.michaelrthon.geneiousplugins.interproscan;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.AminoAcidSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.SequenceAnnotationGenerator.AnnotationGeneratorResult;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * Original author: Michael Thon <mike@michaelrthon.com>
 *
 * @author Neil Bradley.
 */
public class Runner {

    /**
     * This collection is to ensure that multiple runners don't all
     * attempt to run at the same time, possibly breaking EBI's ToS.
     */
    private final static List<UUID> RUNNERS = Collections.synchronizedList(new ArrayList<>());
    private final UUID myKey = UUID.randomUUID();
    private final static int MAXIMUM_CONCURRENT_JOBS = 15;  // Note that this is 1/2 the 30 available from EBI.
    private final static int SLEEP_BETWEEN_JOBS = 250;

    // Instance variables
    private final AnnotatedPluginDocument[] documents;
    private final CompositeProgressListener progress;
    private static Logger logger;
    private final boolean goterms;
    private final boolean pathways;
    private final List<String> appl;
    private final String email;
    private final String featType;
    private final boolean extraFeatures;
    private int index;
    private final Map<Integer, Job> activeJobs = new HashMap<>();
    private final Map<Integer, Job> completedJobs = new HashMap<>();
    


    /**
     * Manages the submission of documents to EBI in such a way as to
     * not violate their terms of use (i.e., no more than 30 concurrent
     * searches at once).
     *
     * @param documents         The sequence documents to annotate.
     * @param options           The user-specified options.
     * @param progressListener  A progress bar.
     */
    public Runner(
            AnnotatedPluginDocument[] documents,
            InterproscanOptions options,
            ProgressListener progressListener
    ) {
        // Submitting, Awaiting, Getting + Start
        int numMessages = documents.length * 3 + 1;

        this.documents = documents;
        progress = new CompositeProgressListener(progressListener, numMessages);
        logger = Logger.getLogger(InterproscanAnnotationGenerator.class.getName());
        goterms = options.isGotermsSelected();
        pathways = options.isPathwaysSelected();
        email = options.getEmailAddress();
        appl = options.getSelectedInterProApps();
        featType = options.getFeatureType();
        extraFeatures = options.isExtraFeatureSelected();
    }

    /**
     * This is the engine of the plugin. It maintains a queue of protein
     * sequences ready for searching.
     *
     * @return  The annotations for Geneious Prime to add to the
     *          the documents.
     *
     * @throws DocumentOperationException .
     */
    public List<AnnotationGeneratorResult> scanSequences() throws DocumentOperationException {

        // 1. Setup Administration structures.
        List<AnnotationGeneratorResult> resultsList = new ArrayList<>(documents.length);
        index = 0;

        // 2. Wait till we are first in queue before running (if other
        //    Runners are already running.
        try {
            progress.beginSubtask("Waiting to execute...");
            waitMyTurn();

            // 3. Maintain a constantly filled queue of tasks, until
            //    either cancelled or completed.
            Iterator<AnnotatedPluginDocument> documentIterator = Arrays.stream(documents).iterator();
            do {
                // 3A. Populate jobs list 1 cycle at a time, up until the maximum.
                while (activeJobs.size() < MAXIMUM_CONCURRENT_JOBS && documentIterator.hasNext()) {
                    addNextJobToQueue(documentIterator.next().getDocument());
                    Thread.sleep(SLEEP_BETWEEN_JOBS);
                }

                // 3B. Cycle through, checking each task status.
                checkForCompletedJobs();

                // Don't hammer the webservice endpoint.
                Thread.sleep(SLEEP_BETWEEN_JOBS);

                // On a slow connection, it's possible to get here with an empty
                // list without having submitted all jobs.
            } while (!activeJobs.isEmpty() || documentIterator.hasNext());

            // Add results in.
            completedJobs.forEach((pos, job) -> resultsList.add(
                    pos,
                    job != null && job.annotationGeneratorResult != null
                            ? job.annotationGeneratorResult
                            : new AnnotationGeneratorResult()
            ));
            progress.setComplete();
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
            throw new DocumentOperationException("Interrupted: " + ex.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during sequence scan", e);
            throw new DocumentOperationException("Unexpected error: " + e.getMessage());
        } finally {
            Runner.RUNNERS.remove(myKey);
        }

        return resultsList;
    }


    /**
     * Checks the active jobs list, and checks if each job is completed.
     */
    private void checkForCompletedJobs() throws DocumentOperationException.Canceled {
        List<Integer> toRemove = new LinkedList<>();
        for (int activeIndex: activeJobs.keySet()) {
            Job job = activeJobs.get(activeIndex);
            checkCancelled();
            String status = job.checkStatus(); 
            switch (status) {
                case "QUEUED":
                case "RUNNING":
                    break;

                case "FINISHED":
                    // 4. Process results.
                    String message = "Getting results for " + job.name;
                    progress.beginNextSubtask(message);
                    logger.log(Level.INFO, message);
                    job.annotationGeneratorResult = postProcessResults(job);
                    toRemove.add(activeIndex);
                    completedJobs.put(activeIndex, job);
                    break;

                case "ERROR":
                case "NOT_FOUND":
                case "FAILURE":
                default:
                    toRemove.add(activeIndex);
                    completedJobs.put(activeIndex, job);
                    String failMessage = "An error occurred with " + job.name + "[" + job.jobid + "]. Status: " + status;
                    progress.beginNextSubtask(failMessage);
                    logger.log(Level.SEVERE, failMessage);
                    break;
            }
        }
        // Remove from active
        toRemove.forEach(activeJobs::remove);
    }


    /**
     * Registers the given document with the EBI webservice.
     *
     * @param document  The document to search.
     *
     * @return A jobid if registration was successful.
     */
    public void addNextJobToQueue(PluginDocument document) {
        AminoAcidSequenceDocument sequenceDocument = (AminoAcidSequenceDocument) document;
        String sequence = trimTerminator(sequenceDocument.getSequenceString());
        String name = sequenceDocument.getName();
        progress.beginNextSubtask("Submitting job for " + name);
        Runner runner = new Runner(documents, null, progress);
        String jobid = runner.submitNewJob(sequence, email, appl, featType, goterms, pathways);
        if (jobid != null && !jobid.isEmpty()) {
            activeJobs.put(index, new Job(jobid, name, sequence.length()));
            progress.beginNextSubtask(name + " submitted, awaiting results.");
            logger.log(Level.INFO, name + " submitted successfully, jobid=" + jobid);
        } else {
            completedJobs.put(index, null);
            progress.beginNextSubtask(name + " had a submission error.");
            progress.beginNextSubtask(name + " skipped.");
            logger.log(Level.SEVERE, name + " did not submit - job skipped.");
        }
        index++;
    }

    /**
     * Removes the termination asterisk from a sequence.
     *
     * @param s The sequence to trim.
     *
     * @return A trimmed sequence.
     */
    private static String trimTerminator(String s) {
        return s.endsWith("*") ? s.substring(0, (s.length() - 1)) : s;
    }

    private String submitNewJob(String sequence, String email, List<String> appl, String featType, boolean goterms, boolean pathways) {
        try {
            URL url = new URL("https://www.ebi.ac.uk/Tools/services/rest/interproscan/run/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
    
            String params = "sequence=" + sequence
                    + "&email=" + email
                    + "&goterms=" + (goterms ? "on" : "off")
                    + "&pathways=" + (pathways ? "on" : "off");
    
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = params.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
    
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
    
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error submitting new job: " + e.getMessage(), e);
            return null;
        }
    }
    


    /**
     * Check for user cancellation.
     */
    private void checkCancelled() throws DocumentOperationException.Canceled {
        if (progress.isCanceled()) {
            logger.log(Level.INFO, "Cancelled by user.");
            throw new DocumentOperationException.Canceled();
        }
    }


    /**
     * Retrieve the results for a specific job.
     *
     * @param job            The requesting job.
     *
     * @return The annotation generator for the given job id.
     */
    private AnnotationGeneratorResult postProcessResults(Job job) {
        try {
            // 1. Create temporary file.
            File outfile = FileUtilities.createTempFile(myKey.toString(), ".xml", true);

            // 2. Retrieve results into temporary file.
            job.downloadResults("xml", outfile);

            // 3. Parse XML from temporary file.
            XmlParser xmlParser = new XmlParser(FileUtilities.getTextFromFile(outfile))
                    .setSeqLength(job.sequenceLength)
                    .setFeatType(featType)
                    .setMakeExtraFeats(extraFeatures);

            return xmlParser.parseXml();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unable to create temporary file for results to download to.");
        }
        return new AnnotationGeneratorResult();
    }


    /**
     * Because EBI has a fair usage limit on number of concurrent jobs
     * per-user, this waits until it is the first Runner in the internal
     * queue of Runners to begin processing it's tasks.
     *
     * @throws DocumentOperationException .
     * @throws InterruptedException       .
     */
    private void waitMyTurn() throws DocumentOperationException, InterruptedException {
        Runner.RUNNERS.add(myKey);
        while (RUNNERS.indexOf(myKey) != 0){
            Thread.sleep(250);
            checkCancelled();
        }
        RUNNERS.remove(myKey);
    }


    /**
     * Class to store administrative data about running jobs.
     */
    private final static class Job {
        public String jobid, name;
        public int sequenceLength;
        public AnnotationGeneratorResult annotationGeneratorResult;

        public Job(String jobid, String name, int sequenceLength) {
            this.jobid = jobid;
            this.name = name;
            this.sequenceLength = sequenceLength;
        }

        public String checkStatus() {
            try {
                URL url = new URL("https://www.ebi.ac.uk/Tools/services/rest/interproscan/status/" + this.jobid);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
        
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error checking job status: " + e.getMessage(), e);
                return "ERROR";
            }
        }

        public void downloadResults(String format, File outfile) {
            try {
                URL url = new URL("https://www.ebi.ac.uk/Tools/services/rest/interproscan/result/" + this.jobid + "/" + format);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                     FileWriter fw = new FileWriter(outfile)) {
        
                    String line;
                    while ((line = br.readLine()) != null) {
                        fw.write(line + "\n");
                    }
                }
        
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error downloading job results: " + e.getMessage(), e);
            }
        }
    }
}