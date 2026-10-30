package seedu.address.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import seedu.address.logic.Messages;
import seedu.address.model.person.Person;

/**
 * Panel containing the list of persons.
 */
public class PersonListPanel extends UiPart<Region> {
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
        super(FXML);
        this.allPersons = allPersons;
        personList.addListener((ListChangeListener<Person>) this::restoreSelectionAfterReplacement);
        personListView.setItems(personList);
        personListView.setCellFactory(listView -> new PersonListViewCell());
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
        placeholder.textProperty().bind(Bindings.when(Bindings.isEmpty(allPersons))
                .then(MESSAGE_NO_CLIENTS)
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
        Person renamedClient = getRenamedClient(change, currentSelection);
        isRestoringSelection = true;
        Platform.runLater(() -> {
            // Names are unique, so a client with the same name is the same client, perhaps edited
            Person newSelection = personListView.getItems().stream().filter(currentSelection::hasSameName)
                    .findFirst().orElse(renamedClient);
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
     * Returns the client that replaced only {@code person} in {@code change}, if {@code person} has also left the
     * address book, as after an edit that renames it. This also recognises a whole-list replacement, as undo and redo
     * use one to restore a previous state. Otherwise, returns null.
     */
    private Person getRenamedClient(ListChangeListener.Change<? extends Person> change, Person person) {
        change.reset();
        if (allPersons.contains(person) || !change.next()) {
            return null;
        }

        List<? extends Person> removed = change.getRemoved();
        List<? extends Person> added = change.getAddedSubList();
        if (removed.equals(List.of(person)) && added.size() == 1) {
            Person renamedClient = added.get(0);
            rememberRename(person, renamedClient);
            return renamedClient;
        }

        // Undo and redo replace the whole list. Treat it as a rename only when the selected client is the sole
        // difference and the direct edit was observed earlier, so a deletion cannot make the selection jump to a
        // different client at the same position.
        if (removed.size() != added.size()) {
            return null;
        }
        int selectedIndex = removed.indexOf(person);
        if (selectedIndex == -1) {
            return null;
        }
        for (int i = 0; i < removed.size(); i++) {
            if (i != selectedIndex && !removed.get(i).equals(added.get(i))) {
                return null;
            }
        }
        Person replacement = added.get(selectedIndex);
        return renamedClients.getOrDefault(person, Set.of()).contains(replacement) ? replacement : null;
    }

    /** Remembers both directions of a rename so that successive undo and redo commands can follow the client. */
    private void rememberRename(Person before, Person after) {
        renamedClients.computeIfAbsent(before, ignored -> new HashSet<>()).add(after);
        renamedClients.computeIfAbsent(after, ignored -> new HashSet<>()).add(before);
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
        personListView.refresh();
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
