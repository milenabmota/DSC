package br.com.cr_system.ui;

import javax.swing.SwingUtilities;

public class MainUI {
  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      var frame = new PainelComandas();
      frame.setVisible(true);
    });
  }
}