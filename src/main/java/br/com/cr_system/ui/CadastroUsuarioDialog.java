package br.com.cr_system.ui;

import br.com.cr_system.HibernateUtil;
import br.com.cr_system.PerfilUsuario;
import br.com.cr_system.Usuario;
import org.hibernate.Session;
import org.hibernate.Transaction;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class CadastroUsuarioDialog extends JDialog {

    private final JTextField txtLogin  = new JTextField();
    private final JTextField txtNome   = new JTextField();
    private final JComboBox<PerfilUsuario> cbPerfil =
            new JComboBox<>(new PerfilUsuario[]{ PerfilUsuario.ATENDENTE, PerfilUsuario.CAIXA });
    private final JPasswordField txtSenha  = new JPasswordField();
    private final JPasswordField txtConf   = new JPasswordField();

    public CadastroUsuarioDialog(Window owner) {
        super(owner, "Cadastrar Usuário", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(8,8));

        JPanel form = new JPanel(new GridBagLayout());
        int r = 0;
        form.add(new JLabel("Login:"),     g(0,r)); form.add(txtLogin,  g(1,r++,2));
        form.add(new JLabel("Nome:"),      g(0,r)); form.add(txtNome,   g(1,r++,2));
        form.add(new JLabel("Perfil:"),    g(0,r)); form.add(cbPerfil,  g(1,r++,2));
        form.add(new JLabel("Senha:"),     g(0,r)); form.add(txtSenha,  g(1,r++,2));
        form.add(new JLabel("Confirmar:"), g(0,r)); form.add(txtConf,   g(1,r++,2));

        JButton btSalvar  = new JButton("Salvar");
        JButton btCancel  = new JButton("Cancelar");
        JPanel  footer    = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(btCancel); footer.add(btSalvar);

        add(form, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        btCancel.addActionListener(e -> dispose());
        btSalvar.addActionListener(e -> salvar());

        getRootPane().setDefaultButton(btSalvar);
        pack();
        setSize(440, getHeight());
        setLocationRelativeTo(owner);
    }

    private void salvar() {
        String login = txtLogin.getText().trim();
        String nome  = txtNome.getText().trim();
        String s1    = new String(txtSenha.getPassword());
        String s2    = new String(txtConf.getPassword());
        PerfilUsuario perfil = (PerfilUsuario) cbPerfil.getSelectedItem();

        if (login.isBlank() || nome.isBlank() || s1.isBlank()) {
            JOptionPane.showMessageDialog(this, "Preencha Login, Nome e Senha."); return;
        }
        if (!s1.equals(s2)) {
            JOptionPane.showMessageDialog(this, "As senhas não conferem."); return;
        }

        String hash = sha256(s1);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long existe = s.createQuery(
                    "select count(u) from Usuario u where lower(u.login)=:l", Long.class)
                    .setParameter("l", login.toLowerCase()).getSingleResult();
            if (existe != null && existe > 0) {
                JOptionPane.showMessageDialog(this, "Já existe usuário com esse login.");
                return;
            }

            Transaction tx = s.beginTransaction();
            Usuario u = new Usuario();
            u.setLogin(login);
            u.setNome(nome);
            u.setPerfil(perfil);      // ATENDENTE ou CAIXA
            u.setSenhaHash(hash);     // salva HASH (não a senha)
            s.persist(u);
            tx.commit();

            JOptionPane.showMessageDialog(this, "Usuário cadastrado.\nHash salvo: " + hash);
            dispose();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage());
        }
    }

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
    // >>> sobrecarga que o compilador reclamou <<<
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