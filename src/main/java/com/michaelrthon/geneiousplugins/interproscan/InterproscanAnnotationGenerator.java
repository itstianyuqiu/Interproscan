package com.michaelrthon.geneiousplugins.interproscan;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.AminoAcidSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import jebl.util.ProgressListener;

import java.util.List;

/**
 * Generic template for AnnotationGenerator
 */
class InterproscanAnnotationGenerator extends SequenceAnnotationGenerator {

    static final String HELP = "Interproscan detects functional domains in proteins";

    @Override
    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("Find Protein Domains With Interproscan...")
                .setMainMenuLocation(GeneiousActionOptions.MainMenu.AnnotateAndPredict);
    }

    @Override
    public String getHelp() {
        return HELP;
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[]{
                new DocumentSelectionSignature(AminoAcidSequenceDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public List<AnnotationGeneratorResult> generate(
            AnnotatedPluginDocument[] documents,
            SelectionRange selectionRange,
            ProgressListener progressListener,
            Options options)
            throws DocumentOperationException {
        try {
            return new Runner(documents, (InterproscanOptions) options, progressListener).scanSequences();
        } catch (Exception e) {
            throw new DocumentOperationException("Unknown failure", e);
        }
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument[] documents,
                              SelectionRange selectionRange) {
        return new InterproscanOptions();
    }

    @Override
    public Options getGeneralOptions() {
        return new InterproscanOptions();
    }
}
