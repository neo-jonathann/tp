package seedu.address.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.VerticalDirection;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Region;
import seedu.address.logic.Messages;
import seedu.address.model.person.Person;

/**
 * Panel containing the list of persons.
 */
public class PersonListPanel extends UiPart<Region> {
    public static final String MESSAGE_DATA_FILE_NOT_READ =
        "The data file could not be read, so the list is empty. Please fix or delete the file and restart.";
    public static final String MESSAGE_NO_CLIENTS = "No clients yet. Use add to add one.";
    public static final String MESSAGE_ALL_CLIENTS_HIDDEN = "No clients to show. Use list to show all clients.";

    private static final String FXML = "PersonListPanel.fxml";

    @FXML
    private Label clientCount;
    @FXML
    private ListView<Person> personListView;
    @FXML
    private Label placeholder;

    private final ReadOnlyObjectWrapper<Person> selectedPerson = new ReadOnlyObjectWrapper<>();
    private final ObservableList<Person> allPersons;
    private final Map<Person, Set<Person>> renamedClients = new HashMap<>();
    private boolean isCompact;
    private boolean isRestoringSelection;

    /**
     * Creates a {@code PersonListPanel} that shows {@code personList}, the clients currently displayed.
     * {@code allPersons} is every client, which decides what the placeholder suggests when none are shown.
     */
    public PersonListPanel(ObservableList<Person> personList, ObservableList<Person> allPersons) {
        this(personList, allPersons, false);
    }

    /**
     * Creates a {@code PersonListPanel} that shows {@code personList}, the clients currently displayed.
     * {@code allPersons} is every client, which decides what the placeholder suggests when none are shown.
     */
    public PersonListPanel(ObservableList<Person> personList, ObservableList<Person> allPersons,
                           boolean isReadOnlyMode) {
        super(FXML);
        this.allPersons = allPersons;
        allPersons.addListener((ListChangeListener<Person>) this::rememberRenamedClients);
        personList.addListener((ListChangeListener<Person>) this::restoreSelectionAfterReplacement);
        personListView.setItems(personList);
        rebuildCells();
        personListView.getSelectionModel().selectedItemProperty().addListener((observable, oldPerson, newPerson) -> {
            // While restoring, the ListView briefly selects by row, which may be another client or none
            if (!isRestoringSelection) {
                selectedPerson.set(newPerson);
            }
        });
        // Recomputes the count whenever personList changes, e.g. after add, delete, find or filter
        clientCount.textProperty().bind(
                Bindings.createStringBinding(() -> Messages.formatClientCount(personList.size()), personList));
        // With no clients at all, list can't help, so only suggest add. Otherwise, a search or filter hides them all
        String noClientsMessage = isReadOnlyMode ? MESSAGE_DATA_FILE_NOT_READ : MESSAGE_NO_CLIENTS;
        placeholder.textProperty().bind(Bindings.when(Bindings.isEmpty(allPersons))
                .then(noClientsMessage)
                .otherwise(MESSAGE_ALL_CLIENTS_HIDDEN));
    }

    /**
     * Returns the selected person property of the underlying list.
     */
    public ReadOnlyObjectProperty<Person> selectedPersonProperty() {
        return selectedPerson.getReadOnlyProperty();
    }

    /**
     * Keeps the same client selected after the displayed list changes, or clears the selection if that client is
     * no longer shown. The ListView would otherwise select by row: an edit replaces the client's {@code Person},
     * and undo, find and filter replace the whole list.
     */
    private void restoreSelectionAfterReplacement(ListChangeListener.Change<? extends Person> change) {
        Person currentSelection = selectedPerson.get();
        // An edit replaces the client and then shows all clients again, so look for the client once, after both
        if (currentSelection == null || isRestoringSelection) {
            return;
        }
        isRestoringSelection = true;
        Platform.runLater(() -> {
            // Names are unique, so a client with the same name is the same client, perhaps edited
            Person newSelection = personListView.getItems().stream().filter(currentSelection::hasSameName)
                    .findFirst().orElseGet(() -> getRenamedClient(currentSelection));
            if (newSelection == null) {
                personListView.getSelectionModel().clearSelection();
            } else {
                personListView.getSelectionModel().select(newSelection);
            }
            isRestoringSelection = false;
            selectedPerson.set(newSelection);
        });
    }

    /**
     * Remembers single-client replacements that change the client's name. The full client list sees the replacement
     * even when the displayed list's current filter removes the renamed client.
     */
    private void rememberRenamedClients(ListChangeListener.Change<? extends Person> change) {
        while (change.next()) {
            if (change.getRemovedSize() != 1 || change.getAddedSize() != 1) {
                continue;
            }
            Person before = change.getRemoved().get(0);
            Person after = change.getAddedSubList().get(0);
            if (!before.hasSameName(after)) {
                rememberRename(before, after);
            }
        }
    }

    /** Remembers both directions of a rename so that selection can follow successive undo and redo commands. */
    private void rememberRename(Person before, Person after) {
        renamedClients.computeIfAbsent(before, ignored -> new HashSet<>()).add(after);
        renamedClients.computeIfAbsent(after, ignored -> new HashSet<>()).add(before);
    }

    /**
     * Returns a remembered renamed version of {@code person} that is in the displayed list, or null if none exists.
     * A client hidden by find or filter must not remain selected merely because it is still in the address book.
     */
    private Person getRenamedClient(Person person) {
        return renamedClients.getOrDefault(person, Set.of()).stream()
                .filter(personListView.getItems()::contains)
                .findFirst()
                .orElse(null);
    }

    /**
     * Scrolls the list about one page in {@code direction}, leaving the selection as it is.
     */
    public void scrollPage(VerticalDirection direction) {
        ScrollBar scrollBar = (ScrollBar) personListView.lookup(".scroll-bar:vertical");
        // The ListView builds its scroll bars when first shown, which is before a key press can reach this
        assert scrollBar != null : "The list has not been shown yet";
        double step = direction == VerticalDirection.UP ? -scrollBar.getVisibleAmount() : scrollBar.getVisibleAmount();
        scrollBar.setValue(Math.clamp(scrollBar.getValue() + step, scrollBar.getMin(), scrollBar.getMax()));
    }

    /**
     * Clears the selected person.
     */
    public void clearSelection() {
        personListView.getSelectionModel().clearSelection();
    }

    /**
     * Shows only the summary fields in each card while the detail panel is open.
     */
    public void setCompact(boolean isCompact) {
        if (this.isCompact == isCompact) {
            return;
        }
        this.isCompact = isCompact;
        rebuildCells();
    }

    /** Recreates every list cell so that its card uses the current compact setting. */
    private void rebuildCells() {
        personListView.setCellFactory(listView -> new PersonListViewCell());
    }

    /**
     * Custom {@code ListCell} that displays the graphics of a {@code Person} using a {@code PersonCard}.
     */
    class PersonListViewCell extends ListCell<Person> {
        PersonListViewCell() {
            // Size each row to the list's width instead of its content, so long details are shortened or wrapped
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(Person person, boolean empty) {
            super.updateItem(person, empty);

            if (empty || person == null) {
                setGraphic(null);
                setText(null);
            } else {
                setGraphic(new PersonCard(person, getIndex() + 1, isCompact).getRoot());
            }
        }
    }

}
