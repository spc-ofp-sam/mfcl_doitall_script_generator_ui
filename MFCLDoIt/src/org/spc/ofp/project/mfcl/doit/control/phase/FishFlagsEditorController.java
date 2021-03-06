/***********************************************************************
 *  Copyright - Secretariat of the Pacific Community                   *
 *  Droit de copie - Secrétariat Général de la Communauté du Pacifique *
 *  http://www.spc.int/                                                *
 ***********************************************************************/
package org.spc.ofp.project.mfcl.doit.control.phase;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.spc.ofp.project.mfcl.doit.control.FormValidator;
import org.spc.ofp.project.mfcl.doit.flag.FishFlag;
import org.spc.ofp.project.mfcl.doit.flag.Fishery;
import org.spc.ofp.project.mfcl.doit.flag.FisheryGroup;

/**
 * FXML Controller class
 * @author Fabrice Bouyé (fabriceb@spc.int)
 */
public final class FishFlagsEditorController extends FormValidator {

    @FXML
    private TableView<Fishery> tableView;
    @FXML
    private TableColumn<Fishery, String> fisheriesColumn;

    /**
     * Creates a new instance.
     */
    public FishFlagsEditorController() {
        // Load flag definitions.
        final URL propertiesURL = getClass().getResource("FishFlagsEditor.properties"); // NOI18N.
        try (final InputStream input = propertiesURL.openStream()) {
            final Properties properties = new Properties();
            properties.load(input);
            final String supportedFlagStr = properties.getProperty("supported.flags"); // NOI18N.
            for (String flagID : supportedFlagStr.split(", ")) { // NOI18N.
                
            }
        } catch (IOException ex) {
            Logger.getLogger(FishFlagsEditorController.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    @Override
    public void dispose() {
        try {
        } finally {
            super.dispose();
        }
    }

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(final URL url, final ResourceBundle bundle) {
        tableView.setItems(fisheries);
        fisheriesColumn.setEditable(false);
        fisheriesColumn.setCellValueFactory(cellDataFeature -> {
            final Fishery fishery = cellDataFeature.getValue();
            return new SimpleStringProperty(fishery.getName());
        });
    }

    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void impl_validateForm() {
    }

    private void populateColumns() {
        tableView.getColumns().remove(1, tableView.getColumns().size());
        final List<TableColumn<Fishery, FisheryGroup>> flagColumns = flags.stream()
                .map(this::createColumnForFlag)
                .collect(Collectors.toList());
        tableView.getColumns().addAll(flagColumns);
        tableView.setColumnResizePolicy((tableView.getColumns().size() < 10) ? TableView.CONSTRAINED_RESIZE_POLICY : TableView.UNCONSTRAINED_RESIZE_POLICY);
        tableView.getSelectionModel().selectedIndexProperty().addListener(observable -> {
            final int rowIndex = tableView.getSelectionModel().getSelectedIndex();
            final Fishery fishery = fisheries.get(rowIndex);
            flags.forEach(flag -> {
                flag.getValues().keySet().forEach(fisheryGroup -> {
                    if (fisheryGroup.getFisheries().contains(fishery)) {
                        fisheryGroup.setSelected(true);
                    } else {
                        fisheryGroup.setSelected(false);
                    }
                });
            });
        });
    }

    /**
     * Create a new table column for given fish flag.
     * @param fishFlag The fish flag.
     * @return A {@code TableColumn<Fishery, FisheryGroup>} instance, never {@code null}.
     */
    private TableColumn<Fishery, FisheryGroup> createColumnForFlag(final FishFlag fishFlag) {
        final TableColumn<Fishery, FisheryGroup> column = new TableColumn<>();
        column.setPrefWidth(35);
        column.setEditable(true);
        column.setSortable(false);
        column.setText(String.valueOf(fishFlag.getId()));
        column.setCellValueFactory(cellDataFeature -> {
            final Fishery fishery = cellDataFeature.getValue();
            final FisheryGroup fisheryGroup = fishFlag.getValues().entrySet().stream()
                    .map(entry -> entry.getKey())
                    .filter(group -> group.getFisheries().contains(fishery))
                    .reduce(null, (a, b) -> b);
            final ObjectProperty<FisheryGroup> property = new SimpleObjectProperty<>(fisheryGroup);
            property.addListener((ObservableValue<? extends FisheryGroup> observableValue, FisheryGroup oldValue, FisheryGroup newValue) -> {
                // Transfer fishery to new group.
                oldValue.getFisheries().remove(fishery);
                newValue.getFisheries().add(fishery);
                // Remove group if empty.
                if (oldValue.getFisheries().isEmpty()) {
                    fishFlag.getValues().remove(oldValue);
                }
                // Add new group if needed.
                if (!fishFlag.getValues().containsKey(newValue)) {
                    fishFlag.getValues().put(newValue, -1);
                }
            });
            return property;
        });
        column.setCellFactory(tableColumn -> new FishGroupTableCell(fishFlag, fisheries));
        return column;
    }

    ////////////////////////////////////////////////////////////////////////////
    /** 
     * List of flags in this editor.
     */
    private final ObservableList<FishFlag> flags = FXCollections.observableList(new LinkedList<>());
    private final ObservableList<FishFlag> flagsReadOnly = FXCollections.unmodifiableObservableList(flags);

    public final ObservableList<FishFlag> getFlags() {
        return flagsReadOnly;
    }

    /** 
     * List of flags in this editor.
     */
    private final ObservableList<Fishery> fisheries = FXCollections.observableList(new LinkedList<>());
    private final ObservableList<Fishery> fisheriesReadOnly = FXCollections.unmodifiableObservableList(fisheries);

    public final ObservableList<Fishery> getFisheries() {
        return fisheriesReadOnly;
    }

    public void loadContent(final List<Fishery> fisheries, final List<FishFlag> flags) {
        this.fisheries.setAll(fisheries);
        this.flags.setAll(flags);
        populateColumns();
    }

}
