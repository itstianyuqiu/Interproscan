package com.michaelrthon.geneiousplugins.interproscan;

import com.biomatters.geneious.publicapi.plugin.GeneiousPlugin;
import com.biomatters.geneious.publicapi.plugin.SequenceAnnotationGenerator;
import com.biomatters.geneious.publicapi.utilities.SystemUtilities;

/**
 * Generic command line plugin template
 */
public class InterproscanPlugin extends GeneiousPlugin {

    @Override
    public String getAuthors() {
        return "Michael Thon and Biomatters";
    }

    @Override
    public String getDescription() {
        return "Search InterPro, an integrated database of protein signatures";
    }

    @Override
    public String getHelp() {
        return InterproscanAnnotationGenerator.HELP;
    }

    @Override
    public int getMaximumApiVersion() {
        return 4;
    }

    @Override
    public String getMinimumApiVersion() {
        if (SystemUtilities.isMac()) {
            return "4.202100";
        } else {
            return "4.201900";
        }
    }

    @Override
    public String getName() {
        return "Interproscan";
    }

    @Override
    public String getVersion() {
        return "2.0";
    }

    @Override
    public SequenceAnnotationGenerator[] getSequenceAnnotationGenerators() {
        return new SequenceAnnotationGenerator[]{
                new InterproscanAnnotationGenerator()
        };
    }
}
