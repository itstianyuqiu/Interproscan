package com.michaelrthon.geneiousplugins.interproscan;

import com.biomatters.geneious.publicapi.components.GEditorPane;
import com.biomatters.geneious.publicapi.plugin.Options;
import org.apache.commons.validator.routines.EmailValidator;
import org.virion.jam.html.SimpleLinkListener;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

class InterproscanOptions extends Options {

    private StringOption emailAddress;
    private ComboBoxOption<OptionValue> featureType;
    private BooleanOption extraFeature;
    private BooleanOption goTerms;
    private BooleanOption pathways;

    // The currently supported member databases|applications {
    //      CDD          Phobius         SignalP_EUK
    //      Coils        PIRSF           SignalP_GRAM_NEGATIVE
    //      Gene3d       PRINTS          SignalP_GRAM_POSITIVE
    //      HAMAP        PrositePatterns SMART
    //      MobiDBLite   PrositeProfiles SuperFamily
    //      Panther      SFLD            TIGRFAM
    //      PfamA        SignalP         TMHMM
    // } Note that the following list is ordered to display as above.
    private final List<String> databaseNames = List.of(
            "CDD",          "Phobius",         "SignalP_EUK",
            "Coils",        "PIRSF",           "SignalP_GRAM_NEGATIVE",
            "Gene3d",       "PRINTS",          "SignalP_GRAM_POSITIVE",
            "HAMAP",        "PrositePatterns", "SMART",
            "MobiDBLite",   "PrositeProfiles", "SuperFamily",
            "Panther",      "SFLD",            "NCBIfam",
            "PfamA",        "SignalP",         "TMHMM"
    );
    private HashMap<String, BooleanOption> databases;

    public final static String FEAT_SEPARATE = "separately";
    public final static String FEAT_QUALIFIERS = "qualifiers";


    /**
     * Build the Options box for Interproscan.
     */
    public InterproscanOptions() {
        addEmailAddressOptions();
        addSearchOptions();
        addExtraFeatureOptions();
        addFeatureTypeOptions();
        addDatabaseOptions();
    }


    /**
     * Add the Email address option.
     */
    private void addEmailAddressOptions() {
        emailAddress = addStringOption("emailAddress", "Email Address", "");
        emailAddress.setDescription("For reporting Interproscan errors");
    }

    private void addSearchOptions() {
        goTerms = addBooleanOption("goterms", "goterms", false);
        goTerms.setDescription("Adds the 'goterms' option to the database search.");
        goTerms.setHidden();
        pathways = addBooleanOption("pathways", "pathways", false);
        pathways.setDescription("Adds the 'pathways' option to the database search.");
        pathways.setHidden();
    }

    /**
     * Choose whether to annotate unknown or broken matches.
     */
    private void addExtraFeatureOptions() {
        extraFeature = addBooleanOption("emptyFeature", "Add features to proteins without InterPro results or with errors", false);
        extraFeature.setDescription("Adds special features to proteins without matches to the InterPro Database or that have errors");
    }


    /**
     * Choose between annotating modes.
     */
    private void addFeatureTypeOptions() {
        List<OptionValue> values = Arrays.asList(
                new OptionValue(
                        FEAT_SEPARATE,
                        "Separately",
                        "Show InterPro terms as separate features that span the length of the protein"
                ),
                new OptionValue(
                        FEAT_QUALIFIERS,
                        "As Qualifiers",
                        "Show InterPro terms as qualifiers on the predicted domains"
                )
        );
        featureType = addComboBoxOption("featureTypes", "Show InterPro Terms:", values, values.get(1));
        featureType.setDescription("Choose how the InterPro features will be displayed");
    }


    /**
     * Add database search options.
     */
    private void addDatabaseOptions() {
        databases = new HashMap<>();
        beginAlignHorizontally("Applications To Run:", true);
        int pos = 0;
        for (String name: databaseNames) {
            BooleanOption booleanOption = addBooleanOption(name, name, true);
            booleanOption.setAdvanced(true);
            databases.put(name, booleanOption);

            // Move to a new line after every four options.
            pos++;
            if (pos % 3 == 0) {
                endAlignHorizontally();
                beginAlignHorizontally("", true);
            }
        }
        endAlignHorizontally();
    }

    public String getEmailAddress() {
        return emailAddress.getValue();
    }

    public boolean isGotermsSelected() {
        return goTerms.getValue();
    }

    public boolean isPathwaysSelected() {
        return pathways.getValue();
    }

    public String getFeatureType() {
        return featureType.getValue().toString();
    }


    /**
     * Check if we are allowing extraneous annotations.
     *
     * @return true if allowed, false otherwise.
     */
    public boolean isExtraFeatureSelected() {
        return extraFeature.getValue();
    }


    /**
     * Create the GUI for the options.
     *
     * @return the GUI panel.
     */
    @Override
    protected JPanel createPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        JPanel defaultPanel = super.createPanel();
        mainPanel.add(defaultPanel, BorderLayout.CENTER);
        GEditorPane citationPane = new GEditorPane();
        citationPane.setEditable(false);
        citationPane.setOpaque(false);
        citationPane.setContentType("text/html");
        citationPane.addHyperlinkListener(new SimpleLinkListener());
        citationPane.setText("<html><br><i><center>"
                + "For more information about InterPro, please visit the <a href=\"https://www.ebi.ac.uk/interpro/\">InterPro homepage </a><br>"
                + "For help using this plugin, see the <a href=\"https://www.michaelrthon.com/geneious-interproscan-plugin/\">online manual</a>"
                + "</center></i><br>");
        mainPanel.add(citationPane, BorderLayout.SOUTH);
        return mainPanel;
    }


    /**
     * Check that the selected options are valid. In fact, only one option matters - EBI insists on a valid email address
     * being passed in.  We cannot confirm the address exists, but we can at least ensure it is of a valid format.
     *
     * @return null if valid, otherwise a message telling the user what to rectify.
     */
    @Override
    public String verifyOptionsAreValid() {
        // Recommended preamble as per Geneious Public API documentation.
        String superMessage = super.verifyOptionsAreValid();
        if (superMessage != null) {
            return superMessage;
        }

        EmailValidator emailValidator = EmailValidator.getInstance();
        if (!emailValidator.isValid(getEmailAddress())) {
            return "Please Enter A Valid Email Address";
        } else {
            return null;
        }
    }


    /**
     * Determine which databases are going to be searched.
     *
     * @return A list with all selected database names.
     */
    public List<String> getSelectedInterProApps() {
        List<String> apps = new ArrayList<>();
        for (String name: databaseNames) {
            if (databases.get(name).getValue()) {
                apps.add(name);
            }
        }
        return apps;
    }
}
