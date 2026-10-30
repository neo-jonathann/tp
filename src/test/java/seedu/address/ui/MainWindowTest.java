package seedu.address.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import seedu.address.logic.LogicManager;
import seedu.address.logic.Messages;
import seedu.address.logic.commands.FindCommand;
import seedu.address.logic.commands.ListCommand;
import seedu.address.model.ModelManager;
import seedu.address.model.person.Person;
import seedu.address.model.person.Phone;
import seedu.address.storage.JsonAddressBookStorage;
import seedu.address.storage.JsonUserPrefsStorage;
import seedu.address.storage.StorageManager;

@ExtendWith(ApplicationExtension.class)
public class MainWindowTest {

    private static final String RAVI_NAME = "Ravi Kumar";
    private static final String ADD_RAVI_COMMAND =
            "add n/Ravi Kumar p/87654321 e/ravi.k@edu.sg a/10 Queenstown Rd t/Math";
    /** The longest name allowed, 50 characters. */
    private static final String LONG_NAME = "Muhammad Hafiz bin Abdul Rahman Ismail Tann (NHHS)";
    private static final String LONG_ADDRESS = "Blk 123 Ang Mo Kio Avenue 3, #12-345, Singapore 560123";
    /** A command whose result is too long to show in full at the minimum width. */
    private static final String LONG_RESULT_COMMAND = "add n/Amirah Tan p/91234567 e/amirah@example.com a/"
            + "Blk 123 Yishun Ave 5 ".repeat(7) + "nt/" + "Weak at algebra ".repeat(12);

    @TempDir
    public Path temporaryFolder;

    private Stage stage;
    /** Looked up once, as the waits below check it off the JavaFX thread, while the scene graph may be changing. */
    private Region detailPlaceholder;

    @Start
    public void start(Stage stage) {
        this.stage = stage;
        MainWindow mainWindow = createMainWindow();
        mainWindow.fillInnerParts();
        detailPlaceholder = (Region) stage.getScene().lookup("#personDetailPanelPlaceholder");
        stage.show();
    }

    @Test
    public void launch_commandBoxHasFocus(FxRobot robot) {
        // Sets the window up as UiManager does: shown first, then filled
        robot.interact(() -> {
            stage.hide();
            MainWindow launchedWindow = createMainWindow();
            launchedWindow.show();
            launchedWindow.fillInnerParts();
        });

        assertEquals(getCommandTextField(), stage.getScene().getFocusOwner());
    }

    @Test
    public void executeCommand_validCommand_showsSuccess(FxRobot robot) {
        enterCommand(robot, ListCommand.COMMAND_WORD);

        assertTrue(getResultMessage().getText().startsWith(ListCommand.MESSAGE_SUCCESS));
        assertFalse(ResultDisplayTest.isShownAsError(getResultDisplay()));
    }

    @Test
    public void executeCommand_searchEmptyAddressBook_showsEmptySearch(FxRobot robot) {
        // The address book is empty, so find says there is nothing to search
        enterCommand(robot, FindCommand.COMMAND_WORD + " Amirah");

        assertEquals(Messages.MESSAGE_NO_CLIENTS_TO_SEARCH, getResultMessage().getText());
        assertTrue(ResultDisplayTest.isShownAsEmptySearch(getResultDisplay()));
    }

    @Test
    public void executeCommand_searchWithNoMatches_showsEmptySearch(FxRobot robot) {
        enterCommand(robot, ADD_RAVI_COMMAND);

        enterCommand(robot, FindCommand.COMMAND_WORD + " Amirah");

        assertEquals(FindCommand.MESSAGE_NO_CLIENTS_FOUND, getResultMessage().getText());
        assertTrue(ResultDisplayTest.isShownAsEmptySearch(getResultDisplay()));
    }

    @Test
    public void executeCommand_errorWrapsAtMinimumWidth_shownInFullWithoutScrolling(FxRobot robot) {
        robot.interact(() -> stage.setWidth(stage.getMinWidth()));
        enterCommand(robot, "edit 1 p/123");

        Label resultMessage = getResultMessage();
        Node resultScrollPane = stage.getScene().lookup("#resultScrollPane");
        assertEquals(Phone.MESSAGE_CONSTRAINTS, ((Text) resultMessage.lookup(".text")).getText());
        assertEquals(resultMessage.getHeight(), resultScrollPane.getLayoutBounds().getHeight(), 0.5);
    }

    @Test
    public void pageDownInCommandBox_longResult_scrollsResult(FxRobot robot) {
        ScrollPane resultScrollPane = showLongResult(robot);

        robot.interact(() -> getCommandTextField().fireEvent(createKeyPressedEvent(KeyCode.PAGE_DOWN)));

        assertTrue(resultScrollPane.getVvalue() > resultScrollPane.getVmin());
    }

    @Test
    public void clickResultText_thenType_textGoesToCommandBox(FxRobot robot) {
        enterCommand(robot, ListCommand.COMMAND_WORD);
        robot.clickOn("#commandTextField");

        robot.clickOn(getResultMessage());

        assertTypedTextGoesToCommandBox(robot);
    }

    @Test
    public void clickLongResultText_thenPageDownAndUp_scrollsResult(FxRobot robot) {
        ScrollPane resultScrollPane = showLongResult(robot);
        robot.clickOn("#commandTextField");
        // The center of the scroll pane is on the text, while the center of the long message is cut off below it
        robot.clickOn(resultScrollPane);

        robot.type(KeyCode.PAGE_DOWN);
        assertTrue(resultScrollPane.getVvalue() > resultScrollPane.getVmin());

        robot.type(KeyCode.PAGE_UP);
        assertEquals(resultScrollPane.getVmin(), resultScrollPane.getVvalue());
    }

    @Test
    public void clickClientCount_thenType_textGoesToCommandBox(FxRobot robot) {
        robot.clickOn("#commandTextField");

        robot.clickOn("#clientCount");

        assertTypedTextGoesToCommandBox(robot);
    }

    @Test
    public void clickStatusBar_thenType_textGoesToCommandBox(FxRobot robot) {
        robot.clickOn("#commandTextField");

        robot.clickOn("#saveLocationStatus");

        assertTypedTextGoesToCommandBox(robot);
    }

    @Test
    public void clickBelowLastClient_thenType_textGoesToCommandBox(FxRobot robot) {
        enterCommand(robot, ADD_RAVI_COMMAND);
        robot.clickOn("#commandTextField");

        robot.clickOn(robot.point(getPersonListView()).atPosition(Pos.BOTTOM_CENTER).atOffset(new Point2D(0, -10)));

        assertTypedTextGoesToCommandBox(robot);
        // Nothing is selected, so the click missed Ravi's card
        assertTrue(getPersonListView().getSelectionModel().isEmpty());
    }

    @Test
    public void openThenCloseFileMenu_thenType_textGoesToCommandBox(FxRobot robot) {
        Menu fileMenu = ((MenuBar) stage.getScene().lookup("#menuBar")).getMenus().get(0);
        robot.clickOn("#commandTextField");

        robot.clickOn("File");
        assertTrue(fileMenu.isShowing());
        robot.clickOn("File");
        assertFalse(fileMenu.isShowing());

        assertTypedTextGoesToCommandBox(robot);
    }

    @Test
    public void commandBox_minimumWidth_promptTextFits(FxRobot robot) {
        robot.interact(() -> stage.setWidth(stage.getMinWidth()));
        TextField commandTextField = getCommandTextField();

        Text promptText = new Text(commandTextField.getPromptText());
        promptText.setFont(commandTextField.getFont());
        Insets insets = commandTextField.getInsets();
        double textWidth = commandTextField.getWidth() - insets.getLeft() - insets.getRight();
        assertTrue(promptText.getLayoutBounds().getWidth() <= textWidth);
    }

    @Test
    public void executeCommand_invalidCommand_showsError(FxRobot robot) {
        enterCommand(robot, "delete 0");

        assertTrue(getResultMessage().getText().startsWith("Invalid command format!"));
        assertTrue(ResultDisplayTest.isShownAsError(getResultDisplay()));
    }

    @Test
    public void toggleTheme_switchesBetweenDarkAndLightThemes(FxRobot robot) {
        Button themeToggleButton = (Button) stage.getScene().lookup("#themeToggleButton");

        assertTrue(stage.getScene().getStylesheets().stream()
                .noneMatch(stylesheet -> stylesheet.endsWith("DarkTheme.css")));
        assertTrue(themeToggleButton.getText().equals("Dark theme"));

        robot.interact(themeToggleButton::fire);

        assertTrue(stage.getScene().getStylesheets().stream()
                .anyMatch(stylesheet -> stylesheet.endsWith("DarkTheme.css")));
        assertTrue(themeToggleButton.getText().equals("Light theme"));

        robot.interact(themeToggleButton::fire);

        assertTrue(stage.getScene().getStylesheets().stream()
                .noneMatch(stylesheet -> stylesheet.endsWith("DarkTheme.css")));
        assertTrue(themeToggleButton.getText().equals("Dark theme"));
    }

    @Test
    public void clickThemeButton_togglesThemeAndFocusStaysInCommandBox(FxRobot robot) {
        robot.clickOn("#commandTextField");

        robot.clickOn("#themeToggleButton");

        assertTrue(stage.getScene().getStylesheets().stream()
                .anyMatch(stylesheet -> stylesheet.endsWith("DarkTheme.css")));
        assertEquals(getCommandTextField(), stage.getScene().getFocusOwner());
    }

    @Test
    public void selectClients_thenClose_detailPanelUpdatesAndListSelectionClears(FxRobot robot)
            throws TimeoutException {
        addThreeClients(robot);

        selectClient(robot, 0);
        assertSelectedClientShown(RAVI_NAME);
        assertTrue(detailPlaceholder.isVisible());

        selectClient(robot, 1);
        assertSelectedClientShown("Amirah Tan");

        Button closeButton = (Button) stage.getScene().lookup("#closeDetailButton");
        robot.interact(closeButton::fire);
        assertTrue(getPersonListView().getSelectionModel().isEmpty());
        waitForDetailPanelToClose();
    }

    @Test
    public void escapeWithDetailOpen_closesDetailPanelAndConsumesEvent(FxRobot robot) throws TimeoutException {
        addAndSelectClient(robot);

        // Other keys reach the command box and leave the panel open
        assertTrue(isKeyPassedToCommandBox(robot, KeyCode.A));
        assertFalse(getPersonListView().getSelectionModel().isEmpty());

        assertFalse(isKeyPassedToCommandBox(robot, KeyCode.ESCAPE));
        assertTrue(getPersonListView().getSelectionModel().isEmpty());
        waitForDetailPanelToClose();
    }

    @Test
    public void selectClientBeforeContentLayout_usesStageWidthForDetailWidth(FxRobot robot)
            throws TimeoutException {
        enterCommand(robot, ADD_RAVI_COMMAND);
        Region clientContent = (Region) stage.getScene().lookup("#clientContent");
        ListView<Person> personListView = getPersonListView();

        robot.interact(() -> {
            stage.hide();
            clientContent.resize(0, clientContent.getHeight());
            personListView.getSelectionModel().select(0);
        });

        assertEquals(0, clientContent.getWidth());
        WaitForAsyncUtils.waitFor(1, TimeUnit.SECONDS, () -> detailPlaceholder.getPrefWidth() > 300);
    }

    @Test
    public void clickClient_thenType_textGoesToCommandBox(FxRobot robot) {
        addAndClickClient(robot);

        assertTypedTextGoesToCommandBox(robot);
        assertEquals(RAVI_NAME, getDetailLabel("#name").getText());
    }

    @Test
    public void clickClient_thenPressUp_recallsLastCommand(FxRobot robot) {
        addAndClickClient(robot);

        robot.type(KeyCode.UP);

        assertEquals(ADD_RAVI_COMMAND, getCommandTextField().getText());
    }

    @Test
    public void clickCloseButton_focusStaysInCommandBox(FxRobot robot) throws TimeoutException {
        addAndClickClient(robot);

        robot.clickOn("#closeDetailButton");

        waitForDetailPanelToClose();
        assertTrue(getCommandTextField().isFocused());
    }

    @Test
    public void clickInsideDetailPanel_thenType_textGoesToCommandBox(FxRobot robot) {
        addAndClickClient(robot);

        robot.clickOn(getDetailLabel("#phone"));

        assertTypedTextGoesToCommandBox(robot);
    }

    @Test
    public void undo_selectedClientMovesRow_staysSelected(FxRobot robot) {
        addThreeClients(robot);
        selectClient(robot, 2);

        enterCommand(robot, "delete 1");
        enterCommand(robot, "undo");

        assertSelectedClientShown("Charlotte Lee");
    }

    @Test
    public void edit_selectedClientInFilteredList_staysSelectedWithNewDetails(FxRobot robot) {
        addThreeClients(robot);
        enterCommand(robot, "filter t/Math");
        selectClient(robot, 1);

        // edit also shows all clients again, so Charlotte moves from row 2 to row 3
        enterCommand(robot, "edit 2 p/99997777");

        assertSelectedClientShown("Charlotte Lee");
        assertEquals("99997777", getDetailLabel("#phone").getText());
    }

    @Test
    public void edit_selectedClientLeavesFilter_staysSelected(FxRobot robot) {
        addThreeClients(robot);
        enterCommand(robot, "filter t/Math");
        selectClient(robot, 1);

        // Charlotte no longer matches the filter, but edit shows all clients again
        enterCommand(robot, "edit 2 t/Physics");

        assertSelectedClientShown("Charlotte Lee");
    }

    @Test
    public void edit_renameSelectedClient_staysSelected(FxRobot robot) {
        addThreeClients(robot);
        selectClient(robot, 2);

        enterCommand(robot, "edit 3 n/Charlotte Lim");

        assertSelectedClientShown("Charlotte Lim");
    }

    @Test
    public void undo_renameSelectedClient_staysSelectedWithRestoredName(FxRobot robot) {
        addThreeClients(robot);
        selectClient(robot, 2);
        enterCommand(robot, "edit 3 n/Charlotte Lim");

        enterCommand(robot, "undo");

        assertSelectedClientShown("Charlotte Lee");
    }

    @Test
    public void redo_renameSelectedClient_staysSelectedWithRenamedName(FxRobot robot) {
        addThreeClients(robot);
        selectClient(robot, 2);
        enterCommand(robot, "edit 3 n/Charlotte Lim");
        enterCommand(robot, "undo");

        enterCommand(robot, "redo");

        assertSelectedClientShown("Charlotte Lim");
    }

    @Test
    public void find_selectedClientNotFound_closesDetailPanel(FxRobot robot) throws TimeoutException {
        addThreeClients(robot);
        enterCommand(robot, "find Charlotte");
        selectClient(robot, 0);

        // One client replaces another, as in an edit, but Charlotte is only hidden, not renamed
        enterCommand(robot, "find Ravi");

        assertTrue(getPersonListView().getSelectionModel().isEmpty());
        waitForDetailPanelToClose();
    }

    @Test
    public void delete_selectedClientWithOthersLeft_closesDetailPanel(FxRobot robot) throws TimeoutException {
        addThreeClients(robot);
        selectClient(robot, 1);

        enterCommand(robot, "delete 2");

        assertTrue(getPersonListView().getSelectionModel().isEmpty());
        waitForDetailPanelToClose();
    }

    @Test
    public void selectClient_minimumWidth_detailPanelFitsInWindow(FxRobot robot) {
        robot.interact(() -> stage.setWidth(stage.getMinWidth()));
        addAndSelectClient(robot);
        waitForDetailPanelToOpen();

        assertDetailPanelFitsInWindow();
    }

    @Test
    public void selectClient_minimumWidth_longNameAndAddressShownInFull(FxRobot robot) {
        robot.interact(() -> stage.setWidth(stage.getMinWidth()));
        enterCommand(robot, "add n/" + LONG_NAME + " p/98765432 e/hafiz@edu.sg a/" + LONG_ADDRESS);
        selectClient(robot, 0);
        waitForDetailPanelToOpen();

        for (String id : new String[] {"#name", "#address"}) {
            Label label = getDetailLabel(id);
            // A wrapped label given less than the height of all its lines ends in "..." instead
            assertTrue(label.getHeight() >= label.prefHeight(label.getWidth()), id + " cut off");
        }
    }

    @Test
    public void resizeWindowWithDetailOpen_detailPanelNarrowsToFit(FxRobot robot) throws TimeoutException {
        robot.interact(() -> stage.setWidth(1200));
        addAndSelectClient(robot);
        // In a window this wide, the panel opens to its widest, 520 pixels
        WaitForAsyncUtils.waitFor(1, TimeUnit.SECONDS, () -> detailPlaceholder.getPrefWidth() >= 519);

        robot.interact(() -> stage.setWidth(stage.getMinWidth()));

        // Times out if the panel keeps its width. Waits on the laid-out width, so the bounds checked next are current
        WaitForAsyncUtils.waitFor(1, TimeUnit.SECONDS, () -> detailPlaceholder.getWidth() < 519);
        assertDetailPanelFitsInWindow();
    }

    /**
     * Creates a main window on {@code stage} for an empty address book, which is saved in {@code temporaryFolder}.
     */
    private MainWindow createMainWindow() {
        Path addressBookFilePath = temporaryFolder.resolve("addressBook.json");
        StorageManager storage = new StorageManager(new JsonAddressBookStorage(addressBookFilePath),
                new JsonUserPrefsStorage(temporaryFolder.resolve("userPrefs.json")));
        return new MainWindow(stage, new LogicManager(new ModelManager(), storage), addressBookFilePath);
    }

    /**
     * Types {@code command} into the command box and presses Enter.
     */
    private void enterCommand(FxRobot robot, String command) {
        TextField commandTextField = getCommandTextField();
        robot.interact(() -> {
            commandTextField.setText(command);
            commandTextField.fireEvent(new ActionEvent());
        });
    }

    /**
     * Shows the result of {@code LONG_RESULT_COMMAND} at the minimum width, and returns the scroll pane it is in.
     */
    private ScrollPane showLongResult(FxRobot robot) {
        robot.interact(() -> stage.setWidth(stage.getMinWidth()));
        enterCommand(robot, LONG_RESULT_COMMAND);
        return (ScrollPane) stage.getScene().lookup("#resultScrollPane");
    }

    /**
     * Types a command, as a user would, and asserts that it lands in the command box.
     */
    private void assertTypedTextGoesToCommandBox(FxRobot robot) {
        robot.write(ListCommand.COMMAND_WORD);
        assertEquals(ListCommand.COMMAND_WORD, getCommandTextField().getText());
    }

    private Label getResultMessage() {
        return (Label) stage.getScene().lookup("#resultMessage");
    }

    private Node getResultDisplay() {
        return stage.getScene().lookup(".result-display");
    }

    @SuppressWarnings("unchecked")
    private ListView<Person> getPersonListView() {
        return (ListView<Person>) stage.getScene().lookup("#personListView");
    }

    private Label getDetailLabel(String selector) {
        Node detailPanel = stage.getScene().lookup("#personDetailPanel");
        return (Label) detailPanel.lookup(selector);
    }

    private TextField getCommandTextField() {
        return (TextField) stage.getScene().lookup("#commandTextField");
    }

    /**
     * Adds a client and selects it, which opens the detail panel.
     */
    private void addAndSelectClient(FxRobot robot) {
        enterCommand(robot, ADD_RAVI_COMMAND);
        selectClient(robot, 0);
    }

    /**
     * Adds a client, then clicks the command box and the client's card, as a user would, and waits for the detail
     * panel to open.
     */
    private void addAndClickClient(FxRobot robot) {
        enterCommand(robot, ADD_RAVI_COMMAND);
        robot.clickOn("#commandTextField");
        robot.clickOn(getPersonListView().lookup("#cardPane"));
        waitForDetailPanelToOpen();
    }

    /**
     * Adds Ravi, Amirah and Charlotte in that order. Ravi and Charlotte are tagged Math.
     */
    private void addThreeClients(FxRobot robot) {
        enterCommand(robot, ADD_RAVI_COMMAND);
        enterCommand(robot, "add n/Amirah Tan p/91234567 e/amirah@edu.sg a/20 Tampines Rd t/English");
        enterCommand(robot, "add n/Charlotte Lee p/98765432 e/charlotte@edu.sg a/30 Bishan St t/Math");
    }

    private void selectClient(FxRobot robot, int zeroBasedIndex) {
        robot.interact(() -> getPersonListView().getSelectionModel().select(zeroBasedIndex));
    }

    /**
     * Asserts that the client called {@code name} is selected in the list and shown in the detail panel.
     */
    private void assertSelectedClientShown(String name) {
        assertEquals(name, getPersonListView().getSelectionModel().getSelectedItem().getName().fullName);
        assertEquals(name, getDetailLabel("#name").getText());
    }

    /**
     * Waits for the detail panel's opening animation to finish, so that it is at its full width.
     */
    private void waitForDetailPanelToOpen() {
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void waitForDetailPanelToClose() throws TimeoutException {
        WaitForAsyncUtils.waitFor(1, TimeUnit.SECONDS, () -> !detailPlaceholder.isVisible());
    }

    /**
     * Asserts that the detail panel's close button and its longest details end inside the window.
     */
    private void assertDetailPanelFitsInWindow() {
        double windowWidth = stage.getScene().getWidth();
        for (String id : new String[] {"#closeDetailButton", "#phone", "#email", "#address"}) {
            Node node = stage.getScene().lookup("#personDetailPanel").lookup(id);
            assertTrue(node.localToScene(node.getBoundsInLocal()).getMaxX() <= windowWidth, id + " cut off");
        }
    }

    /**
     * Fires a press of {@code key} at the command box, where the user types, and returns whether it reached the
     * command box. A key that the window consumes on the way does not.
     */
    private boolean isKeyPassedToCommandBox(FxRobot robot, KeyCode key) {
        TextField commandTextField = getCommandTextField();
        AtomicBoolean isPassedOn = new AtomicBoolean();
        EventHandler<KeyEvent> handler = event -> isPassedOn.set(true);
        robot.interact(() -> {
            commandTextField.addEventHandler(KeyEvent.KEY_PRESSED, handler);
            commandTextField.fireEvent(createKeyPressedEvent(key));
            commandTextField.removeEventHandler(KeyEvent.KEY_PRESSED, handler);
        });
        return isPassedOn.get();
    }

    private KeyEvent createKeyPressedEvent(KeyCode keyCode) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", keyCode, false, false, false, false);
    }
}
