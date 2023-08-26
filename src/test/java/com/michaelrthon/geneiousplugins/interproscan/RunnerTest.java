package com.michaelrthon.geneiousplugins.interproscan;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultAminoAcidSequence;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.SequenceAnnotationGenerator;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import jebl.util.ProgressListener;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RunnerTest {

    @Test
    public void testExampleSequence_withDefaultOptions_returnsExpectedResults() throws DocumentOperationException {
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

        AnnotatedPluginDocument apd = DocumentUtilities.createAnnotatedPluginDocument(sequence);
        AnnotatedPluginDocument[] apds = {apd};
        InterproscanOptions interproscanOptions = new InterproscanOptions();
        // Setting required email option
        interproscanOptions.getOption("emailAddress").setValueFromString("test@geneious.com");
        // All other options can remain default

        List<SequenceAnnotationGenerator.AnnotationGeneratorResult> results = new Runner(apds, interproscanOptions, ProgressListener.EMPTY).scanSequences();
        assertEquals(13, results.get(0).getAnnotationsToAdd().size());
    }
}
