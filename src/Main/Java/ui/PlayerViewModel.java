package Main.Java.ui;

import javafx.beans.property.*;
import javafx.scene.image.Image;

public class PlayerViewModel {

    private final StringProperty name = new SimpleStringProperty();
    private final IntegerProperty ping = new SimpleIntegerProperty();
    private final BooleanProperty operator = new SimpleBooleanProperty();
    private final ObjectProperty<Image> skinHead = new SimpleObjectProperty<>();

    public PlayerViewModel(String name, int ping, boolean operator, Image skinHead) {
        this.name.set(name);
        this.ping.set(ping);
        this.operator.set(operator);
        this.skinHead.set(skinHead);
    }

    public StringProperty nameProperty() { return name; }
    public IntegerProperty pingProperty() { return ping; }
    public BooleanProperty operatorProperty() { return operator; }
    public ObjectProperty<Image> skinHeadProperty() { return skinHead; }

    public String getName() { return name.get(); }
    public int getPing() { return ping.get(); }
    public boolean isOperator() { return operator.get(); }
    public Image getSkinHead() { return skinHead.get(); }
}

