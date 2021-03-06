/***********************************************************************
 *  Copyright - Secretariat of the Pacific Community                   *
 *  Droit de copie - Secrétariat Général de la Communauté du Pacifique *
 *  http://www.spc.int/                                                *
 ***********************************************************************/
package org.spc.ofp.project.mfcl.doit.control.about;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import org.spc.ofp.project.mfcl.doit.FXMLControllerBase;
import org.spc.ofp.project.mfcl.doit.task.print.PrintTextParameters;
import org.spc.ofp.project.mfcl.doit.task.print.PrintTextParametersBuilder;
import org.spc.ofp.project.mfcl.doit.task.print.PrintTextTask;

/**
 * FXML Controller class
 * @author Fabrice Bouyé (fabriceb@spc.int)
 */
public final class AddonPaneController extends FXMLControllerBase {

    @FXML
    private ListView<Addon> addonList;
    @FXML
    private Label nameLabel;
    @FXML
    private Label versionValueLabel;
    @FXML
    private Hyperlink homeLink;
    @FXML
    private Label descriptionLabel;
    @FXML
    private TextArea licenseArea;

    @Override
    public void dispose() {
        try {
            addonProperty().unbind();
            addonProperty().removeListener(invalidationListener);
            setAddon(null);
            updateContent();
            if (loadAddonsService != null) {
                loadAddonsService.cancel();
                loadAddonsService = null;
            }
            if (addonList != null) {
                addonList.getItems().clear();
                addonList.setCellFactory(null);
                addonList = null;
            }
        } finally {
            super.dispose();
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        addonList.setCellFactory(listView -> new ListCell<Addon>() {

            @Override
            protected void updateItem(Addon item, boolean empty) {
                super.updateItem(item, empty);
                String text = null;
                if (!empty && item != null) {
                    text = item.name;
                }
                setText(text);
            }
        });
        //
        addonProperty().bind(addonList.getSelectionModel().selectedItemProperty());
        addonProperty().addListener(invalidationListener);
        //
        loadAddons();
    }

    ////////////////////////////////////////////////////////////////////////////
    private final InvalidationListener invalidationListener = observable -> updateContent();

    ////////////////////////////////////////////////////////////////////////////
    /**
     * Update the content of this pane.
     */
    private void updateContent() {
        nameLabel.setText(null);
        versionValueLabel.setText(null);
        homeLink.setText(null);
        descriptionLabel.setText(null);
        licenseArea.setText(null);
        final Optional<Addon> addonOptional = Optional.ofNullable(getAddon());
        addonOptional.ifPresent(addon -> {
            nameLabel.setText(addon.name);
            versionValueLabel.setText(addon.version);
            homeLink.setText(addon.url);
            descriptionLabel.setText(addon.description);
            loadLicense(addon.license);
        });
    }

    ////////////////////////////////////////////////////////////////////////////
    @FXML
    private void handleHomeLink(final ActionEvent actionEvent) {
        final String url = homeLink.getText();
        if (url != null && !url.trim().isEmpty()) {
            browse(url);
        }
    }

    private Service<Void> licenseWriteService;

    @FXML
    private void handleSaveButton(final ActionEvent actionEvent) {
        // @todo Get last path from preferences.
        final File directory = new File(System.getProperty("user.dir")); // NOI18N.
        final FileChooser dialog = new FileChooser();
        dialog.setInitialDirectory(directory);
        final Optional<File> fileOptional = Optional.ofNullable(dialog.showSaveDialog(licenseArea.getScene().getWindow()));
        fileOptional.ifPresent(file -> {
            // @todo Save last path in preferences.
            if (licenseWriteService == null) {
                licenseWriteService = new Service<Void>() {

                    @Override
                    protected Task<Void> createTask() {
                        return new Task<Void>() {

                            @Override
                            protected Void call() throws Exception {
                                final String text = licenseArea.getText();
                                final List<String> lines = Arrays.asList(text.split("\n")); // NOI18N.
                                Files.write(file.toPath(), lines, Charset.forName("utf-8"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); // NOI18N.
                                return null;
                            }
                        };
                    }
                };
                licenseWriteService.setOnFailed(workerStateEvent -> {
                    final Throwable ex = licenseWriteService.getException();
                    Logger.getLogger(AddonPaneController.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                });
            }
            licenseWriteService.restart();
        });
    }

    @FXML
    private void handleCopyButton(final ActionEvent actionEvent) {
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        content.putString(licenseArea.getText());
        clipboard.setContent(content);
    }

    private Service<Void> printService;

    @FXML
    private void handlePrintButton(final ActionEvent actionEvent) {
        if (printService == null) {
            printService = new Service<Void>() {

                @Override
                protected Task<Void> createTask() {
                    final PrintTextParameters parameters = PrintTextParametersBuilder.create()
                            .text(licenseArea.getText())
                            .textAlignment(TextAlignment.JUSTIFY)
                            .font(licenseArea.getFont())
                            .owner(licenseArea.getScene().getWindow())
                            .build();
                    return new PrintTextTask(parameters);
                }
            };
            printService.setOnFailed(workerStateEvent -> {
                final Throwable ex = printService.getException();
                Logger.getLogger(AddonPaneController.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            });

        }
        printService.restart();
    }
    ////////////////////////////////////////////////////////////////////////////
    private Service<String> loadLicenseService;

    private void loadLicense(final String filename) {
        if (loadLicenseService != null) {
            loadLicenseService.cancel();
        }
        if (loadLicenseService == null) {
            loadLicenseService = new Service<String>() {

                @Override
                protected Task<String> createTask() {
                    return new Task<String>() {

                        @Override
                        protected String call() throws Exception {
                            final URL fileURL = getClass().getResource(filename);
                            final StringWriter stringWriter = new StringWriter();
                            try (final LineNumberReader input = new LineNumberReader(new InputStreamReader(fileURL.openStream())); final PrintWriter output = new PrintWriter(stringWriter)) {
                                for (String line = input.readLine(); line != null; line = input.readLine()) {
                                    output.println(line);
                                }
                            }
                            return stringWriter.toString();
                        }
                    };
                }
            };
            loadLicenseService.setOnSucceeded(workerStateEvent -> {
                final String license = loadLicenseService.getValue();
                licenseArea.setText(license);
            });
            loadLicenseService.setOnCancelled(workerStateEvent -> {
            });
            loadLicenseService.setOnFailed(workerStateEvent -> {
            });
        }
        loadLicenseService.restart();
    }

    private Service<List<Addon>> loadAddonsService;

    private void loadAddons() {
        if (loadAddonsService != null) {
            loadAddonsService.cancel();
        }
        if (loadAddonsService == null) {
            loadAddonsService = new Service<List<Addon>>() {

                @Override
                protected Task<List<Addon>> createTask() {
                    return new Task<List<Addon>>() {

                        @Override
                        protected List<Addon> call() throws Exception {
                            final URL fileURL = getClass().getResource("addon.properties"); // NOI18N.
                            final Properties properties = new Properties();
                            try (final InputStream input = fileURL.openStream()) {
                                properties.load(input);
                            }
                            final String addonDefinitions = properties.getProperty("addons"); // NOI18N.
                            final String[] addonIds = addonDefinitions.split(","); // NOI18N.
                            final List<Addon> result = new LinkedList<>();
                            for (final String addonId : addonIds) {
                                final Addon addon = new Addon();
                                addon.name = properties.getProperty(String.format("%s.name", addonId)); // NOI18N.
                                addon.version = properties.getProperty(String.format("%s.version", addonId)); // NOI18N.
                                addon.url = properties.getProperty(String.format("%s.url", addonId)); // NOI18N.
                                addon.description = properties.getProperty(String.format("%s.description", addonId)); // NOI18N.
                                addon.license = properties.getProperty(String.format("%s.license", addonId)); // NOI18N.
                                result.add(addon);
                            }
                            return result;
                        }
                    };
                }
            };
            loadAddonsService.setOnSucceeded(workerStateEvent -> {
                final List<Addon> addons = loadAddonsService.getValue();
                addonList.getItems().setAll(addons);
                addonList.getSelectionModel().select(0);
            });
            loadAddonsService.setOnCancelled(workerStateEvent -> {
            });
            loadAddonsService.setOnFailed(workerStateEvent -> {
            });
        }
        loadAddonsService.restart();
    }

    ////////////////////////////////////////////////////////////////////////////
    private final ObjectProperty<Addon> addon = new SimpleObjectProperty<>(this, "addon"); // NOI18N.

    final void setAddon(final Addon value) {
        addon.set(value);
    }

    final Addon getAddon() {
        return addon.get();
    }

    final ObjectProperty<Addon> addonProperty() {
        return addon;
    }
}
