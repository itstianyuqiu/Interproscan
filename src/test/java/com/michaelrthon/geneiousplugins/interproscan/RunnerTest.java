package com.michaelrthon.geneiousplugins.interproscan;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultAminoAcidSequence;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.plugin.SequenceAnnotationGenerator;
import jebl.util.ProgressListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class RunnerTest {

    private AnnotatedPluginDocument apd;
    private InterproscanOptions interproscanOptions;

    @BeforeEach
    public void setUp() {
        TestGeneious.initialize();
        DefaultAminoAcidSequence sequence = new DefaultAminoAcidSequence(
            "NARH",
            "MKIRSQVGMVLNLDKCIGCHTCSVTCKNVWTSREGVEYAWFNNVETKPGQGFPTDWENQEKYKGGWIRKINGKLQPRMGNRAMLLGKIFANPHLPG" +
                "IDDYYEPFDFDYQNLHTAPEGSKSQPIARPRSLITGERMAKIEKGPNWEDDLGGEFDKLAKDKNFDNIQKAMYSQFENTFMMYLPRLCEHCLNPAC" +
                "VATCPSGAIYKREEDGIVLIDQDKCRGWRMCITGCPYKKIYFNWKSGKSEKCIFCYPRIEAGQPTVCSETCVGRIRYLGVLLYDADAIERAASTEN" +
                "EKDLYQRQLDVFLDPNDPKVIEQAIKDGIPLSVIEAAQQSPVYKMAMEWKLALPLHPEYRTLPMVWYVPPLSPIQSAADAGELGSNGILPDVESLR" +
                "IPVQYLANLLTAGDTKPVLRALKRMLAMRHYKRAETVDGKVDTRALEEVGLTEAQAQEMYRYLAIANYEDRFVVPSSHRELAREAFPEKNGCGFTF" +
                "GDGCHGSDTKFNLFNSRRIDAIDVTSKTEPHP"
        );

        apd = DocumentUtilities.createAnnotatedPluginDocument(sequence);
        interproscanOptions = new InterproscanOptions();
        // Setting required email option
        interproscanOptions.getOption("emailAddress").setValueFromString("test@geneious.com");
    }

    @Test
    public void testExampleSequence_withDefaultOptions_returnsExpectedResults() throws DocumentOperationException {
        AnnotatedPluginDocument[] apds = {apd};
        List<SequenceAnnotationGenerator.AnnotationGeneratorResult> results = new Runner(apds, interproscanOptions, ProgressListener.EMPTY).scanSequences();
        assertEquals(13, results.get(0).getAnnotationsToAdd().size());
    }

    @Test
    public void testEmptySequenceInput_returnsEmptyResults() throws DocumentOperationException {
        AnnotatedPluginDocument[] apds = new AnnotatedPluginDocument[0];
        List<SequenceAnnotationGenerator.AnnotationGeneratorResult> results = new Runner(apds, interproscanOptions, ProgressListener.EMPTY).scanSequences();
        assertTrue(results.isEmpty());
    }

    @Test
    public void testInvalidEmailInput_throwsException() {
        interproscanOptions.getOption("emailAddress").setValueFromString("invalidEmail");
        AnnotatedPluginDocument[] apds = {apd};

        assertThrows(DocumentOperationException.class, () -> {
            new Runner(apds, interproscanOptions, ProgressListener.EMPTY).scanSequences();
        });
    }
}
