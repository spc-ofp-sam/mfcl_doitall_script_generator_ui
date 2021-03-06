/***********************************************************************
 *  Copyright - Secretariat of the Pacific Community                   *
 *  Droit de copie - Secrétariat Général de la Communauté du Pacifique *
 *  http://www.spc.int/                                                *
 ***********************************************************************/
package org.spc.ofp.project.mfcl.doit.control.codeeditor;

import java.net.URL;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.layout.Region;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.spc.ofp.project.mfcl.doit.Disposable;

/**
 * CSS and XML code editor with syntax highlight.
 * <br/>We use HTML5 for this editor.
 * <br/>The underlying JavaScript editor is using <a href="http://codemirror.net/">CodeMirror</a>.
 * @author Fabrice Bouyé (fabriceb@spc.int)
 */
public class CodeEditor extends Region implements Disposable {

    private static final Logger LOGGER = Logger.getLogger(CodeEditor.class.getName());

    /**
     * Code modes in this editor.
     * @author Fabrice Bouyé (fabriceb@spc.int)
     */
    public enum Mode {

        SHELL;
    }

    /**
     * The delegated web view.
     */
    private final WebView webView = new WebView();

    /**
     * Creates a new instance.
     */
    public CodeEditor() {
        super();
        setId("codeEditor"); // NOI18N.
        getStyleClass().add("code-editor"); // NOI18N.
        getChildren().add(webView);
        webView.setContextMenuEnabled(false);
        //
        initialized.addListener(initializedChangeListener);
        Platform.runLater(this::initializePeer);
    }

    @Override
    public void dispose() {
        initialized.removeListener(initializedChangeListener);
        webView.getEngine().getLoadWorker().stateProperty().removeListener(peerLoadStateChangeListener);
        getChildren().remove(webView);
        modeProperty().removeListener(modeInvalidationListener);
        textProperty().removeListener(textInvalidationListener);
    }

    ////////////////////////////////////////////////////////////////////////////
    /**
     * Initialize the peer control.
     */
    private void initializePeer() {
        webView.getEngine().getLoadWorker().stateProperty().addListener(peerLoadStateChangeListener);
        final URL htlmURL = getClass().getResource("CodeEditor.html"); // NOI18N.
        webView.getEngine().load(htlmURL.toExternalForm());
    }

    /**
     * Indicates whether this control has been initialized.
     */
    private final ReadOnlyBooleanWrapper initialized = new ReadOnlyBooleanWrapper(this, "initialized", false);

    public boolean isInitialized() {
        return initialized.get();
    }

    public final ReadOnlyBooleanProperty initializedProperty() {
        return initialized.getReadOnlyProperty();
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        final double width = getWidth();
        final double height = getHeight();
        final Insets insets = getInsets();
        webView.resizeRelocate(insets.getLeft(), insets.getTop(), width - (insets.getLeft() + insets.getRight()), height - (insets.getTop() + insets.getBottom()));
    }

    ////////////////////////////////////////////////////////////////////////////
    private boolean isEditing = false;

    /**
     * Called whenever the initialized property changes value.
     */
    private final ChangeListener<Boolean> initializedChangeListener = (ObservableValue<? extends Boolean> observableValue, Boolean oldValue, Boolean newValue) -> {
        if (newValue) {
            final Optional<EventHandler<ActionEvent>> onInitializedOptional = Optional.ofNullable(getOnInitialized());
            onInitializedOptional.ifPresent(onInitialized -> {
                try {
                    ActionEvent actionEvent = new ActionEvent(CodeEditor.this, null);
                    onInitialized.handle(actionEvent);
                } catch (Throwable ex) {
                    LOGGER.log(Level.WARNING, ex.getMessage(), ex);
                }
            });
        }
    };

    /**
     * Called whenever the text is invalidated.
     */
    private final InvalidationListener textInvalidationListener = (final Observable observable) -> {
        if (!isEditing) {
            Platform.runLater(() -> {
                clearPeerContent();
                pushTextToPeer();
            });
        }
    };

    /**
     * Called whenever the mode is invalidated.
     */
    private final InvalidationListener modeInvalidationListener = (final Observable observable) -> {
        Platform.runLater(this::switchPeerMode);
    };

    /**
     * Called when the state of the web engine loader changes.
     */
    private final ChangeListener<Worker.State> peerLoadStateChangeListener = (final ObservableValue<? extends Worker.State> observableValue, final Worker.State oldValue, final Worker.State newValue) -> {
        switch (newValue) {
            case SUCCEEDED:
                LOGGER.log(Level.FINEST, "Code editor peer load succeeded.");
                // Installing bridge object.
                final JSObject jsObject = (JSObject) webView.getEngine().executeScript("window"); // NOI18N.
                jsObject.setMember("java", new Bridge()); // NOI18N.
                // Add property listeners.
                modeProperty().addListener(modeInvalidationListener);
                textProperty().addListener(textInvalidationListener);
                // Finishing initialization.
                initialized.set(true);
                break;
            case CANCELLED:
                LOGGER.log(Level.FINEST, "Code editor peer load cancelled.");
                break;
            case FAILED:
                final Throwable ex = webView.getEngine().getLoadWorker().getException();
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                break;
            default:
        }
    };

    ////////////////////////////////////////////////////////////////////////////
    /**
     * The bridge class that is provided to JavaScript for bidirectionnal dialog.
     * <br/>Had to be public to avoid some exceptions from being thrown.
     * @author Fabrice Bouyé (fabriceb@spc.int)
     */
    public final class Bridge {

        public void updateText() {
            LOGGER.entering(Bridge.class.getName(), "updateText");
            pullTextFromPeer();
            LOGGER.exiting(Bridge.class.getName(), "updateText");
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    /**
     * Changes the editor mode of the HTML5 peer.
     */
    private void switchPeerMode() {
        final Optional<Mode> modeOptional = Optional.ofNullable(getMode());
        modeOptional.ifPresent(mode -> {
            final String command = String.format("setMode('%s');", mode.toString().toLowerCase()); // NOI18N.
            webView.getEngine().executeScript(command);
        });
    }

    /**
     * Clear the content of the HTML5 peer.
     */
    private void clearPeerContent() {
        try {
            isEditing = true;
            webView.getEngine().executeScript("clearText()"); // NOI18N.        
        } finally {
            isEditing = false;
        }
    }

    /**
     * Push text from this control to our HTML5 peer.
     */
    private void pushTextToPeer() {
        final Optional<String> textOptional = Optional.ofNullable(getText());
        textOptional.ifPresent(text -> {
            try {
                isEditing = true;
                final String content = text
                        .replaceAll("\\\\", "\\\\\\\\") // NOI18N.
                        .replaceAll("\n", "\\\\n") // NOI18N.
                        .replaceAll("'", "\\\\'"); // NOI18N.
                final String command = String.format("setText('%s');", content); // NOI18N.
                webView.getEngine().executeScript(command);
            } finally {
                isEditing = false;
            }
        });
    }

    /**
     * Pull text from our HTML5 peer to this control.
     */
    private void pullTextFromPeer() {
        if (isEditing) {
            return;
        }
        try {
            isEditing = true;
            final String result = (String) webView.getEngine().executeScript("getText()"); // NOI18N.     
            setText(result);
        } finally {
            isEditing = false;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    /**
     * Current mode of the editor.
     */
    private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(this, "mode"); // NOI18N.

    public final Mode getMode() {
        return mode.get();
    }

    public final void setMode(final Mode value) {
        mode.set(value);
    }

    public final ObjectProperty<Mode> modeProperty() {
        return mode;
    }

    /**
     * Current content of the editor.
     */
    private final StringProperty text = new SimpleStringProperty(this, "text"); // NOI18N.

    public final String getText() {
        return text.get();
    }

    public final void setText(final String value) {
        text.set(value);
    }

    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Action to execute once the editor has been initialized.
     */
    private final ObjectProperty<EventHandler<ActionEvent>> onInitialized = new SimpleObjectProperty<>(this, "onInitialized"); // NOI18N.

    public final EventHandler<ActionEvent> getOnInitialized() {
        return onInitialized.get();
    }

    public final void setOnInitialized(final EventHandler<ActionEvent> value) {
        onInitialized.set(value);
    }

    public final ObjectProperty<EventHandler<ActionEvent>> onInitializedProperty() {
        return onInitialized;
    }
}
