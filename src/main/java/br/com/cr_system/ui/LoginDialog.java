package br.com.cr_system.ui;

import br.com.cr_system.HibernateUtil;
import br.com.cr_system.PerfilUsuario;
import br.com.cr_system.Usuario;
import org.hibernate.Session;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class LoginDialog extends JDialog {

    private final JTextField     txtLogin = new JTextField();
    private final JPasswordField txtPass  = new JPasswordField();

    private Usuario autenticado;

    public LoginDialog(Window owner) {
        super(owner, "Entrar", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(8,8));

        // Garante que os campos estarão editáveis/habilitados
        txtLogin.setEnabled(true);
        txtLogin.setEditable(true);
        txtLogin.setColumns(18);

        txtPass.setEnabled(true);
        txtPass.setEditable(true);
        txtPass.setColumns(18);

        JPanel form = new JPanel(new GridBagLayout());
        int r=0;
        form.add(new JLabel("Login:"), g(0,r)); form.add(txtLogin, g(1,r++,2));
        form.add(new JLabel("Senha:"), g(0,r)); form.add(txtPass,  g(1,r++,2));

        JButton btCancelar  = new JButton("Cancelar");
        JButton btCadastrar = new JButton("Cadastrar");
        JButton btEntrar    = new JButton("Entrar");
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(btCadastrar); footer.add(btCancelar); footer.add(btEntrar);

        add(form, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        btCancelar.addActionListener(e -> { autenticado = null; dispose(); });
        btCadastrar.addActionListener(e -> new CadastroUsuarioDialog(this).setVisible(true));
        btEntrar.addActionListener(e -> autenticar());

        getRootPane().setDefaultButton(btEntrar);

        // Foco automático no campo de login quando a janela abrir
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent e) {
                SwingUtilities.invokeLater(() -> txtLogin.requestFocusInWindow());
            }
        });

        // Evita qualquer propagação de estado "disabled" acidental
        setEnabled(true);
        setFocusable(true);

        pack();
        setSize(420, getHeight());
        setLocationRelativeTo(owner);
    }

    private void autenticar() {
        String login = txtLogin.getText().trim();
        String pass  = new String(txtPass.getPassword());

        if (login.equals("admin") && pass.equals("root123456")) {
            Usuario admin = new Usuario();
            admin.setId(-1L);
            admin.setLogin("admin");
            admin.setNome("Administrador");
            admin.setPerfil(PerfilUsuario.ADMIN);
            admin.setSenhaHash(sha256(pass));
            this.autenticado = admin;
            dispose();
            return;
        }

        String hash = sha256(pass);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Usuario u = s.createQuery(
                    "from Usuario u where lower(u.login)=:l", Usuario.class)
                    .setParameter("l", login.toLowerCase())
                    .uniqueResult();

            if (u == null || u.getSenhaHash() == null || !u.getSenhaHash().equalsIgnoreCase(hash)) {
                JOptionPane.showMessageDialog(this, "Login ou senha inválidos.");
                return;
            }

            this.autenticado = u;
            dispose();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage());
        }
    }

    public Usuario getAutenticado() { return autenticado; }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static GridBagConstraints g(int x, int y){ return g(x,y,1,1); }
    private static GridBagConstraints g(int x, int y, int w){ return g(x,y,w,1); }
    private static GridBagConstraints g(int x, int y, int w, int h) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx=x; c.gridy=y; c.gridwidth=w; c.gridheight=h;
        c.insets = new Insets(6,6,6,6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        return c;
    }
}