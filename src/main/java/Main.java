import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("CLI")) {
            DynamicDBManagerCLI.start();
        } else {
            SwingUtilities.invokeLater(() -> new DynamicDBManagerGUI().start());
        }
    }
}
