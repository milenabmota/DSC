package br.com.cr_system.ui;

import br.com.cr_system.Comanda;
import br.com.cr_system.HibernateUtil;
import br.com.cr_system.Produto;
import org.hibernate.Session;
import org.hibernate.Transaction;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;

public class AdicionarPedidoDialog extends JDialog {
    private final JComboBox<Comanda> cbComanda = new JComboBox<>();
    private final JComboBox<Produto> cbProduto = new JComboBox<>();
    private final JSpinner spQtd = new JSpinner(new SpinnerNumberModel(1,1,1000,1));

    public interface OnConfirm { void add(long comandaId, long produtoId, int qtd); }

    public AdicionarPedidoDialog(Window owner, OnConfirm onConfirm) {
        super(owner, "Adicionar Pedido", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        cbProduto.setEditable(true);
        cbComanda.setRenderer(new PainelComandas.ComandaRenderer());
        cbProduto.setRenderer(new PainelComandas.ProdutoRenderer());
        spQtd.setPreferredSize(new Dimension(56, spQtd.getPreferredSize().height));

        // UI
        JPanel box = new JPanel(new GridBagLayout());
        box.setBorder(new TitledBorder("Pedido"));
        int r = 0;
        box.add(new JLabel("Comanda:"), g(0,r));               box.add(cbComanda, g(1,r++,2));
        box.add(new JLabel("Produto (digite para buscar):"), g(0,r)); box.add(cbProduto, g(1,r,1));
        box.add(new JLabel("Qtd:"), g(2,r));                   box.add(spQtd, g(3,r++));

        JButton ok = new JButton("Adicionar");
        JButton cancel = new JButton("Cancelar");
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(cancel); footer.add(ok);

        getContentPane().setLayout(new BorderLayout(8,8));
        add(box, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        // atalhos
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e2 -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        ok.addActionListener(e -> {
            Object com = cbComanda.getSelectedItem();
            Produto prod = getProdutoSelecionado();
            int qtd = (Integer) spQtd.getValue();
            if (!(com instanceof Comanda) || prod == null || qtd <= 0) {
                JOptionPane.showMessageDialog(this, "Preencha Comanda, Produto e Qtd.");
                return;
            }
            onConfirm.add(((Comanda) com).getId(), prod.getId(), qtd);
            dispose();
        });
        cancel.addActionListener(e -> dispose());

        carregarComandasEProdutos();
        instalarAutocompleteProduto();

        // pronto pra digitar
        SwingUtilities.invokeLater(() -> {
            JTextField editor = (JTextField) cbProduto.getEditor().getEditorComponent();
            cbProduto.setSelectedItem("");
            editor.setText("");
            editor.requestFocusInWindow();
        });

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    // === resolve Produto mesmo quando a seleção atual é String (texto do editor)
    private Produto getProdutoSelecionado() {
        // 1) se o model já tiver Produto selecionado, use-o
        Object mSel = cbProduto.getModel().getSelectedItem();
        if (mSel instanceof Produto p) return p;

        // 2) caso contrário, tente resolver pelo texto do editor
        String typed = ((JTextField) cbProduto.getEditor().getEditorComponent()).getText();
        String nome = sanitizeNome(typed);

        ComboBoxModel<Produto> model = cbProduto.getModel();
        Produto unicoMatch = null;
        for (int i = 0; i < model.getSize(); i++) {
            Produto p = model.getElementAt(i);
            String n = p.getNome() == null ? "" : p.getNome().trim();
            if (n.equalsIgnoreCase(nome)) return p;        // match exato
            if (n.toLowerCase().contains(nome.toLowerCase())) unicoMatch = p; // mantém um possível
        }
        return unicoMatch; // se só sobrou um na lista, serve também
    }

    // remove " — R$ 22,00" e afins
    private String sanitizeNome(String s) {
        if (s == null) return "";
        s = s.trim();
        int i = s.indexOf(" — "); if (i >= 0) s = s.substring(0, i);
        i = s.toLowerCase().indexOf(" r$"); if (i >= 0) s = s.substring(0, i);
        return s.trim();
    }

    private void carregarComandasEProdutos() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Comanda> abertas = s.createQuery(
                    "from Comanda c where c.fechada=false order by c.id", Comanda.class).list();
            DefaultComboBoxModel<Comanda> mCom = new DefaultComboBoxModel<>();
            for (Comanda c : abertas) mCom.addElement(c);
            cbComanda.setModel(mCom);

            List<Produto> produtos = s.createQuery("from Produto order by nome", Produto.class).list();
            if (produtos.isEmpty()) {
                Transaction tx = s.beginTransaction();
                s.persist(PainelComandas.novoProduto("X-Burger", 18.0, 50));
                s.persist(PainelComandas.novoProduto("Refrigerante Lata", 7.0, 100));
                s.persist(PainelComandas.novoProduto("Porção de Batata", 22.0, 40));
                tx.commit();
                produtos = s.createQuery("from Produto order by nome", Produto.class).list();
            }
            DefaultComboBoxModel<Produto> mProd = new DefaultComboBoxModel<>();
            for (Produto p : produtos) mProd.addElement(p);
            cbProduto.setModel(mProd);
            cbProduto.setSelectedItem(""); // mantém como texto digitado
        }
    }

    // Autocomplete: atualiza a lista e preserva o texto digitado
    private void instalarAutocompleteProduto() {
        final JTextField editor = (JTextField) cbProduto.getEditor().getEditorComponent();
        editor.getDocument().addDocumentListener(new DocumentListener() {
            private long lastTs = 0;

            private void go() {
                long now = System.currentTimeMillis();
                if (now - lastTs < 120) return; // debounce
                lastTs = now;

                SwingUtilities.invokeLater(() -> {
                    if (!cbProduto.isDisplayable()) return;

                    String typed = editor.getText();
                    String q = sanitizeNome(typed).toLowerCase();

                    try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                        List<Produto> lst = (q.isBlank())
                                ? s.createQuery("from Produto order by nome", Produto.class).list()
                                : s.createQuery("from Produto p where lower(p.nome) like :q order by p.nome", Produto.class)
                                   .setParameter("q", "%"+q+"%").list();

                        @SuppressWarnings("unchecked")
                        DefaultComboBoxModel<Produto> m = (DefaultComboBoxModel<Produto>) cbProduto.getModel();
                        m.removeAllElements();
                        for (Produto p : lst) m.addElement(p);

                        // IMPORTANTÍSSIMO: volta a seleção para o texto digitado,
                        // para não substituir o editor pelo primeiro item.
                        cbProduto.setSelectedItem(typed);

                        cbProduto.setPopupVisible(!lst.isEmpty() && editor.isFocusOwner());
                    } catch (Exception ignored) {}
                });
            }

            @Override public void insertUpdate(DocumentEvent e){ go(); }
            @Override public void removeUpdate(DocumentEvent e){ go(); }
            @Override public void changedUpdate(DocumentEvent e){ go(); }
        });
    }

    // Helpers
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