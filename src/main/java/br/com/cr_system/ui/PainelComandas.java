package br.com.cr_system.ui;

import br.com.cr_system.*;
import org.hibernate.Session;
import org.hibernate.Transaction;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class PainelComandas extends JFrame {

    // ====== Botões da barra ======
    private final JButton btAbrir      = new JButton("Abrir Comanda");
    private final JButton btAdd        = new JButton("Adicionar Pedido");
    private final JButton btPagar      = new JButton("Registrar Pagamento");
    private final JButton btFechar     = new JButton("Fechar Comanda");
    private final JButton btVerPedidos = new JButton("Ver Pedidos");
    private final JButton btNovoProd   = new JButton("Novo Produto");

    // ====== Log ======
    private final JTextArea txtLog = new JTextArea(12, 60);

    public PainelComandas() {
        super("CR System — Painel de Comandas");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 620));
        setLocationByPlatform(true);
        getContentPane().setLayout(new BorderLayout(10, 10));

        // Barra
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        barra.add(btAbrir);
        barra.add(btAdd);
        barra.add(btPagar);
        barra.add(btFechar);
        barra.add(btVerPedidos);
        barra.add(btNovoProd);
        getContentPane().add(barra, BorderLayout.NORTH);

        // Centro (placeholder)
        getContentPane().add(new JPanel(), BorderLayout.CENTER);

        // Log
        txtLog.setEditable(false);
        txtLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(txtLog);
        sp.setBorder(new TitledBorder("Movimentações"));
        getContentPane().add(sp, BorderLayout.SOUTH);

        // Actions
        btNovoProd.addActionListener(e -> cadastrarProdutoDialog());
        btAbrir.addActionListener(e -> abrirComandaDialog());
        btAdd.addActionListener(e ->
            new AdicionarPedidoDialog(this, this::adicionarPedido).setVisible(true)
        );
        btPagar.addActionListener(e -> mostrarDialogRegistrarPagamento());
        btFechar.addActionListener(e -> mostrarDialogFecharComanda());
        btVerPedidos.addActionListener(e -> mostrarDialogVerPedidos());

        // Pré-carrega produtos de exemplo se estiver vazio
        carregarProdutosSeVazio();

        pack();
    }

    // =========================================================
    // ================== AÇÕES / REGRAS =======================
    // =========================================================

    /** Agora com campo de MESA também */
    private void abrirComandaDialog() {
        JTextField txtCliente = new JTextField();
        JTextField txtMesa    = new JTextField();

        JPanel p = new JPanel(new GridLayout(0,1,6,6));
        JPanel row1 = new JPanel(new BorderLayout(6,6));
        row1.add(new JLabel("Cliente (opcional):"), BorderLayout.NORTH);
        row1.add(txtCliente, BorderLayout.CENTER);

        JPanel row2 = new JPanel(new BorderLayout(6,6));
        row2.add(new JLabel("Mesa (opcional):"), BorderLayout.NORTH);
        row2.add(txtMesa, BorderLayout.CENTER);

        p.add(row1);
        p.add(row2);

        JDialog d = new JDialog(this, "Abrir comanda", Dialog.ModalityType.APPLICATION_MODAL);
        JButton ok = new JButton("Abrir");
        JButton cancel = new JButton("Cancelar");
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        foot.add(cancel); foot.add(ok);

        d.getContentPane().setLayout(new BorderLayout(8,8));
        d.add(p, BorderLayout.CENTER);
        d.add(foot, BorderLayout.SOUTH);

        // Tamanho um pouco maior pra ficar confortável
        d.setPreferredSize(new Dimension(360, 200));

        d.getRootPane().setDefaultButton(ok);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        ok.addActionListener(e -> {
            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = s.beginTransaction();

                // atendente demo
                var atendente = s.createQuery("from Usuario where login = :l", Usuario.class)
                        .setParameter("l","maria").uniqueResult();
                if (atendente == null) {
                    atendente = new Usuario();
                    atendente.setLogin("maria");
                    atendente.setNome("Maria Silva");
                    atendente.setPerfil(PerfilUsuario.ATENDENTE);
                    atendente.setSenhaHash("x");
                    s.persist(atendente);
                }

                Comanda c = new Comanda();
                c.setAtendente(atendente);
                String cliente = txtCliente.getText().isBlank() ? null : txtCliente.getText().trim();
                String mesa    = txtMesa.getText().isBlank() ? null : txtMesa.getText().trim();
                c.setClienteNome(cliente);
                c.setMesa(mesa);
                c.setFechada(false);
                // tipoPagamento será definido na etapa de pagamento/fechamento
                s.persist(c);
                tx.commit();

                String extra = "";
                if (cliente != null) extra += " para " + cliente;
                if (mesa != null) extra += (extra.isEmpty() ? " Mesa: " : " | Mesa: ") + mesa;

                log("🧾 Comanda #" + c.getId() + " aberta por " + atendente.getNome() + extra);
            } catch (Exception ex) { erro(ex); }
            d.dispose();
        });
        cancel.addActionListener(e -> d.dispose());

        d.pack(); d.setResizable(false); d.setLocationRelativeTo(this); d.setVisible(true);
    }

    /** Chamado pelo AdicionarPedidoDialog (com ids e qtd) */
    private void adicionarPedido(long comandaId, long produtoId, int qtd) {
        if (qtd <= 0) { alerta("Quantidade inválida."); return; }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Comanda com = s.get(Comanda.class, comandaId);
            if (com == null) { alerta("Comanda não encontrada."); return; }
            if (Boolean.TRUE.equals(com.getFechada())) { alerta("Comanda já está fechada."); return; }

            Produto p = s.get(Produto.class, produtoId);
            if (p == null) { alerta("Produto não encontrado."); return; }

            Transaction tx = s.beginTransaction();
            Pedido ped = new Pedido();
            ped.setComanda(com);
            ped.setProduto(p);
            ped.setQuantidade(qtd);
            ped.setValorTotal(p.getPreco() * qtd);
            s.persist(ped);
            tx.commit();

            log(String.format(
                    "📝 Pedido #%d criado na comanda #%d (%s — Qtd: %d, Subtotal: %s)",
                    ped.getId(), com.getId(), p.getNome(), qtd, money(ped.getValorTotal())
            ));
            mostrarTotais(com.getId());
        } catch (Exception ex) { erro(ex); }
    }

    private void mostrarDialogRegistrarPagamento() {
        JDialog d = new JDialog(this, "Registrar Pagamento", Dialog.ModalityType.APPLICATION_MODAL);

        JComboBox<Comanda> cbComanda = new JComboBox<>();
        cbComanda.setRenderer(new ComandaRenderer());
        JComboBox<TipoPagamento> cbTipo = new JComboBox<>(TipoPagamento.values());
        JFormattedTextField txtValor = new JFormattedTextField(NumberFormat.getNumberInstance());
        txtValor.setColumns(10);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Comanda> abertas = s.createQuery("from Comanda c where c.fechada=false order by c.id", Comanda.class).list();
            DefaultComboBoxModel<Comanda> m = new DefaultComboBoxModel<>();
            for (Comanda c : abertas) m.addElement(c);
            cbComanda.setModel(m);
        } catch (Exception ex) { erro(ex); }

        JPanel box = new JPanel(new GridBagLayout());
        box.setBorder(new TitledBorder("Pagamento (padrão)"));
        int r=0;
        box.add(new JLabel("Comanda:"), g(0,r)); box.add(cbComanda, g(1,r++,2));
        box.add(new JLabel("Tipo:"), g(0,r));    box.add(cbTipo,    g(1,r++,2));
        box.add(new JLabel("Valor:"), g(0,r));   box.add(txtValor,  g(1,r++,2));

        JButton btOk = new JButton("Registrar");
        JButton btCancel = new JButton("Cancelar");
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        foot.add(btCancel); foot.add(btOk);

        d.getContentPane().setLayout(new BorderLayout(8,8));
        d.add(box, BorderLayout.CENTER);
        d.add(foot, BorderLayout.SOUTH);

        d.getRootPane().setDefaultButton(btOk);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        btCancel.addActionListener(e -> d.dispose());
        btOk.addActionListener(e -> {
            Comanda com = (Comanda) cbComanda.getSelectedItem();
            if (com == null) { alerta("Selecione a comanda."); return; }
            double valor = parseValor(txtValor);
            if (valor <= 0) { alerta("Informe um valor > 0."); return; }
            TipoPagamento tp = (TipoPagamento) cbTipo.getSelectedItem();

            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                com = s.get(Comanda.class, com.getId());
                Transaction tx = s.beginTransaction();
                Pagamento pg = new Pagamento();
                pg.setComanda(com);
                pg.setTipo(tp);
                pg.setDataHora(LocalDateTime.now());
                pg.setValor(valor);
                s.persist(pg);
                tx.commit();

                log("💳 Pagamento #" + pg.getId() + " de " + money(valor)
                        + " via " + labelPagamento(tp) + " (comanda #" + com.getId() + ")");
                mostrarTotais(com.getId());
            } catch (Exception ex) { erro(ex); }
            d.dispose();
        });

        d.pack(); d.setResizable(false); d.setLocationRelativeTo(this); d.setVisible(true);
    }

    private void mostrarDialogFecharComanda() {
        JDialog d = new JDialog(this, "Fechar Comanda", Dialog.ModalityType.APPLICATION_MODAL);

        JComboBox<Comanda> cbComanda = new JComboBox<>();
        cbComanda.setRenderer(new ComandaRenderer());
        JComboBox<TipoPagamento> cbTipo = new JComboBox<>(TipoPagamento.values());

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Comanda> abertas = s.createQuery("from Comanda c where c.fechada=false order by c.id", Comanda.class).list();
            DefaultComboBoxModel<Comanda> m = new DefaultComboBoxModel<>();
            for (Comanda c : abertas) m.addElement(c);
            cbComanda.setModel(m);
        } catch (Exception ex) { erro(ex); }

        JPanel box = new JPanel(new GridBagLayout());
        box.setBorder(new TitledBorder("Fechar Comanda"));
        int r=0;
        box.add(new JLabel("Comanda:"), g(0,r)); box.add(cbComanda, g(1,r++,2));
        box.add(new JLabel("Tipo de pagamento:"), g(0,r)); box.add(cbTipo, g(1,r++,2));

        JButton btOk = new JButton("Fechar");
        JButton btCancel = new JButton("Cancelar");
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        foot.add(btCancel); foot.add(btOk);

        d.getContentPane().setLayout(new BorderLayout(8,8));
        d.add(box, BorderLayout.CENTER);
        d.add(foot, BorderLayout.SOUTH);

        d.getRootPane().setDefaultButton(btOk);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        btCancel.addActionListener(e -> d.dispose());
        btOk.addActionListener(e -> {
            Comanda c = (Comanda) cbComanda.getSelectedItem();
            if (c == null) { alerta("Escolha a comanda a fechar."); return; }
            TipoPagamento tipo = (TipoPagamento) cbTipo.getSelectedItem();

            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                c = s.get(Comanda.class, c.getId());

                Double total = s.createQuery(
                        "select coalesce(sum(p.valorTotal),0) from Pedido p where p.comanda.id = :id", Double.class)
                        .setParameter("id", c.getId()).getSingleResult();

                Double pago = s.createQuery(
                        "select coalesce(sum(pag.valor),0) from Pagamento pag where pag.comanda.id = :id", Double.class)
                        .setParameter("id", c.getId()).getSingleResult();

                double restante = total - pago;
                Transaction tx = s.beginTransaction();
                if (restante > 0) {
                    Pagamento pg = new Pagamento();
                    pg.setComanda(c);
                    pg.setTipo(tipo);
                    pg.setDataHora(LocalDateTime.now());
                    pg.setValor(restante);
                    s.persist(pg);
                }
                c.setTipoPagamento(tipo);
                c.setFechada(true);
                tx.commit();

                if (restante > 0) {
                    log("✅ Comanda #" + c.getId() + " fechada. Pagamento final de " + money(restante)
                            + " via " + labelPagamento(tipo));
                } else {
                    log("✅ Comanda #" + c.getId() + " fechada sem valor restante. Tipo: " + labelPagamento(tipo));
                }
            } catch (Exception ex) { erro(ex); }
            d.dispose();
        });

        d.pack(); d.setResizable(false); d.setLocationRelativeTo(this); d.setVisible(true);
    }

    // ===== Relatório de pedidos da comanda =====
    private void mostrarDialogVerPedidos() {
        JDialog d = new JDialog(this, "Pedidos da Comanda", Dialog.ModalityType.APPLICATION_MODAL);
        d.setLayout(new BorderLayout(8,8));

        JComboBox<Comanda> cbComanda = new JComboBox<>();
        cbComanda.setRenderer(new ComandaRenderer());
        carregarTodasComandas(cbComanda);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.add(new JLabel("Comanda:"));
        top.add(cbComanda);
        JButton btReload = new JButton("Atualizar");
        top.add(btReload);

        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"Item", "Produto", "Qtd", "Unitário", "Subtotal"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 0,2 -> Integer.class;
                    default -> String.class;
                };
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(2).setMaxWidth(70);
        JScrollPane jsp = new JScrollPane(table);

        JLabel lbTotal = new JLabel("Total: " + money(0));
        lbTotal.setFont(lbTotal.getFont().deriveFont(Font.BOLD));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(lbTotal);

        d.add(top, BorderLayout.NORTH);
        d.add(jsp, BorderLayout.CENTER);
        d.add(bottom, BorderLayout.SOUTH);

        Runnable load = () -> {
            model.setRowCount(0);
            Comanda c = (Comanda) cbComanda.getSelectedItem();
            if (c == null) return;
            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                Long id = c.getId();
                List<Pedido> pedidos = s.createQuery(
                        "from Pedido p join fetch p.produto where p.comanda.id = :id order by p.id",
                        Pedido.class).setParameter("id", id).getResultList();

                double total = 0;
                int item = 1;
                for (Pedido p : pedidos) {
                    double unit = p.getProduto().getPreco();
                    double sub = p.getValorTotal();
                    total += sub;
                    model.addRow(new Object[]{
                            item++,
                            p.getProduto().getNome(),
                            p.getQuantidade(),
                            money(unit),
                            money(sub)
                    });
                }
                lbTotal.setText("Total: " + money(total));
            } catch (Exception ex) { erro(ex); }
        };

        cbComanda.addActionListener(e -> load.run());
        btReload.addActionListener(e -> {
            carregarTodasComandas(cbComanda);
            load.run();
        });

        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        d.setSize(700, 420);
        d.setLocationRelativeTo(this);
        d.setVisible(true);

        load.run();
    }

    // --- Diálogo: Cadastrar Produto ---
    private void cadastrarProdutoDialog() {
        JTextField tNome = new JTextField();
        JFormattedTextField tPreco = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        JSpinner spEst = new JSpinner(new SpinnerNumberModel(0,0,100000,1));

        JPanel p = new JPanel(new GridLayout(0,2,6,6));
        p.add(new JLabel("Nome:"));    p.add(tNome);
        p.add(new JLabel("Preço:"));   p.add(tPreco);
        p.add(new JLabel("Estoque:")); p.add(spEst);

        JDialog d = new JDialog(this, "Cadastrar Produto", Dialog.ModalityType.APPLICATION_MODAL);
        JButton ok = new JButton("Salvar");
        JButton cancel = new JButton("Cancelar");
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        foot.add(cancel); foot.add(ok);

        d.getContentPane().setLayout(new BorderLayout(8,8));
        d.add(p, BorderLayout.CENTER);
        d.add(foot, BorderLayout.SOUTH);

        d.getRootPane().setDefaultButton(ok);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        ok.addActionListener(e -> {
            String nome = tNome.getText().trim();
            double preco;
            try {
                tPreco.commitEdit();
                Object v = tPreco.getValue();
                preco = (v instanceof Number) ? ((Number)v).doubleValue()
                        : Double.parseDouble(String.valueOf(v).replace(",", "."));
            } catch (Exception ex) { preco = 0d; }

            int est = (Integer) spEst.getValue();

            if (nome.isBlank() || preco <= 0) {
                JOptionPane.showMessageDialog(this, "Informe Nome e Preço (> 0).");
                return;
            }

            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = s.beginTransaction();
                Produto prod = novoProduto(nome, preco, est);
                s.persist(prod);
                tx.commit();
                log("🍔 Produto #" + prod.getId() + " cadastrado: " + prod.getNome());
            } catch (Exception ex) { erro(ex); }

            d.dispose();
        });
        cancel.addActionListener(e -> d.dispose());

        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void carregarTodasComandas(JComboBox<Comanda> combo) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Comanda> todas = s.createQuery("from Comanda c order by c.fechada, c.id", Comanda.class).list();
            DefaultComboBoxModel<Comanda> m = new DefaultComboBoxModel<>();
            for (Comanda c : todas) m.addElement(c);
            combo.setModel(m);
        } catch (Exception ex) { erro(ex); }
    }

    // cria exemplos se não houver produtos
    private void carregarProdutosSeVazio() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long count = s.createQuery("select count(p) from Produto p", Long.class).getSingleResult();
            if (count == 0) {
                Transaction tx = s.beginTransaction();
                s.persist(novoProduto("X-Burger", 18.0, 50));
                s.persist(novoProduto("Refrigerante Lata", 7.0, 100));
                s.persist(novoProduto("Porção de Batata", 22.0, 40));
                tx.commit();
                log("📦 Produtos de exemplo criados.");
            }
        } catch (Exception ex) { erro(ex); }
    }

    // =========================================================
    // =================== HELPERS / OUTROS ====================
    // =========================================================

    private void mostrarTotais(Long comandaId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Double total = s.createQuery("select coalesce(sum(p.valorTotal),0) from Pedido p where p.comanda.id = :id",
                    Double.class).setParameter("id", comandaId).getSingleResult();
            Double pagos = s.createQuery("select coalesce(sum(pg.valor),0) from Pagamento pg where pg.comanda.id = :id",
                    Double.class).setParameter("id", comandaId).getSingleResult();
            double rest = total - pagos;
            log(String.format("📊 Comanda #%d | Total: %s | Pago: %s | Restante: %s",
                    comandaId, money(total), money(pagos), money(rest)));
        } catch (Exception ex) { erro(ex); }
    }

    static Produto novoProduto(String nome, double preco, int estoque) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setPreco(preco);
        p.setEstoque(estoque);
        return p;
    }

    private double parseValor(JFormattedTextField f) {
        try {
            f.commitEdit();
            Object v = f.getValue();
            if (v instanceof Number) return ((Number) v).doubleValue();
            return Double.parseDouble(Objects.toString(v, "0").replace(",", "."));
        } catch (Exception e) { return 0d; }
    }

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private String money(double v) { return BRL.format(v); }

    private String labelPagamento(TipoPagamento tp) {
        if (tp == null) return "-";
        return switch (tp) {
            case DINHEIRO       -> "Dinheiro";
            case CARTAO_CREDITO -> "Cartão de Crédito";
            case CARTAO_DEBITO  -> "Cartão de Débito";
            case PIX            -> "PIX";
        };
    }

    private void alerta(String s) { JOptionPane.showMessageDialog(this, s); }
    private void erro(Exception ex) { ex.printStackTrace(); alerta("Erro: " + ex.getMessage()); }
    private void log(String s) { txtLog.append(s + "\n"); txtLog.setCaretPosition(txtLog.getDocument().getLength()); }

    // GridBag helpers
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

    // Renderers
    public static class ComandaRenderer extends BasicComboBoxRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Comanda c) {
                String cliente = c.getClienteNome();
                setText("#" + c.getId() + (cliente!=null && !cliente.isBlank() ? (" - " + cliente) : ""));
            } else if (value == null) setText("");
            return this;
        }
    }
    public static class ProdutoRenderer extends BasicComboBoxRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Produto p) {
                setText(p.getNome() + "  —  " + NumberFormat.getCurrencyInstance(new Locale("pt","BR")).format(p.getPreco()));
            } else if (value == null) setText("");
            return this;
        }
    }
}