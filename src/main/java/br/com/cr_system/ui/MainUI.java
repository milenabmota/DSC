package br.com.cr_system.ui;

import br.com.cr_system.Usuario;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

public class MainUI {

    public static void main(String[] args) {
        // Locale brasileiro para formatos (R$ etc.)
        Locale.setDefault(new Locale("pt", "BR"));

        // Look & Feel do sistema operacional
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {}

        // Inicia UI
        SwingUtilities.invokeLater(() -> {
            // (opcional) ajusta fonte padrão um pouco maior
            UIManager.put("Label.font",       UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 13f));
            UIManager.put("Button.font",      UIManager.getFont("Button.font").deriveFont(Font.PLAIN, 13f));
            UIManager.put("TextField.font",   UIManager.getFont("TextField.font").deriveFont(Font.PLAIN, 13f));
            UIManager.put("PasswordField.font",UIManager.getFont("PasswordField.font").deriveFont(Font.PLAIN, 13f));
            UIManager.put("ComboBox.font",    UIManager.getFont("ComboBox.font").deriveFont(Font.PLAIN, 13f));
            UIManager.put("Table.font",       UIManager.getFont("Table.font").deriveFont(Font.PLAIN, 13f));

            // 1) Login
            LoginDialog login = new LoginDialog(null);
            login.setVisible(true);
            Usuario usuario = login.getAutenticado();
            if (usuario == null) {
                // cancelou/fechou
                System.exit(0);
                return;
            }

            // 2) Abre painel principal
            PainelComandas app = new PainelComandas(usuario);
            app.setVisible(true);
        });
    }
}