package br.com.cr_system.ui;

import br.com.cr_system.*;
import org.hibernate.Session;
import org.hibernate.Transaction;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.mindrot.jbcrypt.BCrypt;


public class PainelComandas extends JFrame {

    private final Usuario usuarioLogado;

    private final JButton btAbrir      = new JButton("Abrir Comanda");
    private final JButton btAdd        = new JButton("Adicionar Pedido");
    private final JButton btPagar      = new JButton("Registrar Pagamento");
    private final JButton btFechar     = new JButton("Fechar Comanda");
    private final JButton btVerPedidos = new JButton("Ver Pedidos");
    private final JButton btNovoProd   = new JButton("Novo Produto");
    private final JButton btRelatorios = new JButton("Relatórios"); // só ADMIN vê

    private final JTextArea txtLog = new JTextArea(12, 60);

    // Para recarregar relatório automaticamente após fechar comanda
    private JDialog relatoriosAberto;
    private Runnable relatoriosReload;

    public PainelComandas(Usuario usuarioLogado) {
        super("CR System — Painel de Comandas");
        this.usuarioLogado = usuarioLogado;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 640));
        setLocationByPlatform(true);
        getContentPane().setLayout(new BorderLayout(10, 10));

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        barra.add(btAbrir);
        barra.add(btAdd);
        barra.add(btPagar);
        barra.add(btFechar);
        barra.add(btVerPedidos);
        barra.add(btNovoProd);
        if (usuarioLogado.getPerfil() == PerfilUsuario.ADMIN) {
            barra.add(Box.createHorizontalStrut(12));
            barra.add(btRelatorios);
        }
        getContentPane().add(barra, BorderLayout.NORTH);

        getContentPane().add(new JPanel(), BorderLayout.CENTER);

        txtLog.setEditable(false);
        txtLog.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(txtLog);
        sp.setBorder(new TitledBorder("Movimentações"));
        getContentPane().add(sp, BorderLayout.SOUTH);

        btNovoProd.addActionListener(e -> cadastrarProdutoDialog());
        btAbrir.addActionListener(e -> abrirComandaDialog());
        btAdd.addActionListener(e -> new AdicionarPedidoDialog(this, this::adicionarPedido).setVisible(true));
        btPagar.addActionListener(e -> mostrarDialogRegistrarPagamento());
        btFechar.addActionListener(e -> mostrarDialogFecharComanda());
        btVerPedidos.addActionListener(e -> mostrarDialogVerPedidos());
        btRelatorios.addActionListener(e -> mostrarDialogRelatorios());

        carregarProdutosSeVazio();

        setTitle(getTitle() + "  |  Usuário: " + usuarioLogado.getNome() + " (" + usuarioLogado.getLogin() + ")");
        pack();
    }

    // =================== Abrir Comanda ===================
    private void abrirComandaDialog() {
        JTextField txtCliente = new JTextField();
        JTextField txtMesa    = new JTextField();

        JPanel p = new JPanel(new GridLayout(0,1,6,6));
        p.add(new JLabel("Cliente (opcional):"));
        p.add(txtCliente);
        p.add(new JLabel("Mesa (opcional):"));
        p.add(txtMesa);

        int ok = JOptionPane.showConfirmDialog(this, p, "Abrir comanda", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();

            // resolve atendente persistido
            Usuario atendenteDb;
            if (usuarioLogado.getId() != null && usuarioLogado.getId() > 0) {
                atendenteDb = s.get(Usuario.class, usuarioLogado.getId());
            } else {
                atendenteDb = s.createQuery("from Usuario u where u.login = :l", Usuario.class)
                        .setParameter("l", "admin")
                        .uniqueResult();
                if (atendenteDb == null) {
                    atendenteDb = new Usuario();
                    atendenteDb.setLogin("admin");
                    atendenteDb.setNome("Administrador");
                    atendenteDb.setPerfil(PerfilUsuario.ADMIN);
                    atendenteDb.setSenhaHash("-");
                    s.persist(atendenteDb);
                    s.flush();
                }
            }

            Comanda c = new Comanda();
            c.setAtendente(atendenteDb);
            String cliente = txtCliente.getText().isBlank() ? null : txtCliente.getText().trim();
            String mesa    = txtMesa.getText().isBlank()    ? null : txtMesa.getText().trim();
            c.setClienteNome(cliente);
            c.setMesa(mesa);
            c.setFechada(false);

            s.persist(c);
            tx.commit();

            log("🧾 Comanda #" + c.getId() + " aberta por " + atendenteDb.getNome()
                    + (cliente != null ? (" para " + cliente) : "")
                    + (mesa != null ? (" | Mesa " + mesa) : ""));
        } catch (Exception ex) { erro(ex); }

        carregarComandasAbertas(); // opcional
    }

    // =================== Adicionar Pedido (callback do dialog) ===================
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

    // =================== Registrar Pagamento ===================
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
                // quem registrou:
                Usuario registrador = (usuarioLogado.getId() != null && usuarioLogado.getId() > 0)
                        ? s.get(Usuario.class, usuarioLogado.getId())
                        : s.createQuery("from Usuario u where u.login = :l", Usuario.class)
                        .setParameter("l","admin").uniqueResult();
                pg.setRegistrador(registrador);

                s.persist(pg);
                tx.commit();

                log("💳 Pagamento #" + pg.getId() + " de " + money(valor)
                        + " via " + labelPagamento(tp)
                        + " (comanda #" + com.getId() + ")"
                        + " — registrado por " + usuarioLogado.getNome() + " (" + usuarioLogado.getLogin() + ")");
                mostrarTotais(com.getId());
            } catch (Exception ex) { erro(ex); }
            d.dispose();
        });

        d.pack(); d.setResizable(false); d.setLocationRelativeTo(this); d.setVisible(true);
    }

    // =================== Fechar Comanda ===================
    private void mostrarDialogFecharComanda() {
        JDialog d = new JDialog(this, "Fechar Comanda", Dialog.ModalityType.APPLICATION_MODAL);

        JComboBox<Comanda> cbComanda = new JComboBox<>();
        cbComanda.setRenderer(new ComandaRenderer());

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Comanda> abertas = s.createQuery(
                    "from Comanda c where c.fechada=false order by c.id", Comanda.class).list();
            DefaultComboBoxModel<Comanda> m = new DefaultComboBoxModel<>();
            for (Comanda c : abertas) m.addElement(c);
            cbComanda.setModel(m);
        } catch (Exception ex) { erro(ex); }

        JPanel box = new JPanel(new GridBagLayout());
        box.setBorder(new TitledBorder("Fechar Comanda"));
        int r=0;
        box.add(new JLabel("Comanda:"), g(0,r));
        box.add(cbComanda, g(1,r++,2));

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

            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                c = s.get(Comanda.class, c.getId());

                Double total = s.createQuery(
                        "select coalesce(sum(p.valorTotal),0) from Pedido p where p.comanda.id = :id",
                        Double.class).setParameter("id", c.getId()).getSingleResult();

                Double pago = s.createQuery(
                        "select coalesce(sum(pg.valor),0) from Pagamento pg where pg.comanda.id = :id",
                        Double.class).setParameter("id", c.getId()).getSingleResult();

                double restante = total - pago;

                if (restante > 0.0001) {
                    alerta("Ainda faltam " + money(restante)
                            + " para esta comanda. Registre o pagamento antes de fechar.");
                    return;
                }

                Transaction tx = s.beginTransaction();
                c.setFechada(true);
                tx.commit();

                log("🔒 Comanda #" + c.getId() + " fechada (Total: " + money(total)
                        + " | Pago: " + money(pago) + ").");

                // Se o relatório estiver aberto, recarrega agora
                if (relatoriosAberto != null && relatoriosAberto.isShowing() && relatoriosReload != null) {
                    relatoriosReload.run();
                }
            } catch (Exception ex) { erro(ex); }

            d.dispose();
        });

        d.pack(); d.setResizable(false); d.setLocationRelativeTo(this); d.setVisible(true);
    }

    // =================== Ver Pedidos (só abertas) ===================
    private void mostrarDialogVerPedidos() {
        JDialog d = new JDialog(this, "Pedidos da Comanda", Dialog.ModalityType.APPLICATION_MODAL);
        d.setLayout(new BorderLayout(8,8));

        JComboBox<Comanda> cbComanda = new JComboBox<>();
        cbComanda.setRenderer(new ComandaRenderer());
        carregarComandasAbertas(cbComanda);

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
        lbTotal.setFont(lbTotal.getFont().deriveFont(java.awt.Font.BOLD));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(lbTotal);

        d.add(top, BorderLayout.NORTH);
        d.add(jsp, BorderLayout.CENTER);
        d.add(bottom, BorderLayout.SOUTH);

        Runnable load = () -> {
            model.setRowCount(0);
            Comanda sel = (Comanda) cbComanda.getSelectedItem();
            if (sel == null) { lbTotal.setText("Total: " + money(0)); return; }

            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                Boolean fechada = s.createQuery(
                        "select c.fechada from Comanda c where c.id = :id", Boolean.class)
                        .setParameter("id", sel.getId())
                        .getSingleResult();

                if (Boolean.TRUE.equals(fechada)) { lbTotal.setText("Total: " + money(0)); return; }

                List<Pedido> pedidos = s.createQuery(
                        "from Pedido p join fetch p.produto where p.comanda.id = :id order by p.id",
                        Pedido.class).setParameter("id", sel.getId()).getResultList();

                double total = 0;
                int item = 1;
                for (Pedido p : pedidos) {
                    double unit = p.getProduto().getPreco();
                    double sub  = p.getValorTotal();
                    total += sub;
                    model.addRow(new Object[]{ item++, p.getProduto().getNome(), p.getQuantidade(), money(unit), money(sub) });
                }
                lbTotal.setText("Total: " + money(total));
            } catch (Exception ex) { erro(ex); }
        };

        cbComanda.addActionListener(e -> load.run());
        btReload.addActionListener(e -> { carregarComandasAbertas(cbComanda); load.run(); });

        d.setSize(700, 420);
        d.setLocationRelativeTo(this);
        load.run();                 // carrega antes
        d.setVisible(true);         // depois abre
    }

    // =================== Relatórios (ADMIN) ===================
    private void mostrarDialogRelatorios() {
    if (usuarioLogado.getPerfil() != PerfilUsuario.ADMIN) {
        alerta("Acesso restrito ao administrador.");
        return;
    }

    JDialog d = new JDialog(this, "Relatórios de Vendas", Dialog.ModalityType.APPLICATION_MODAL);
    d.setLayout(new BorderLayout(10,10));

    JLabel lbTotal = new JLabel("Carregando...");
    lbTotal.setFont(lbTotal.getFont().deriveFont(14f));
    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
    top.add(new JLabel("Total vendido: "));
    top.add(lbTotal);

    final DefaultTableModel mUser = new DefaultTableModel(
            new Object[]{"Usuário", "Total Vendido"}, 0){
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    JTable tbUser = new JTable(mUser);
    JScrollPane spUser = new JScrollPane(tbUser);
    spUser.setBorder(new TitledBorder("Vendido por usuário (registrador do pagamento)"));

    final DefaultTableModel mItens = new DefaultTableModel(
            new Object[]{"Usuário (atendente)", "Produto", "Qtd", "Total"}, 0){
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    JTable tbItens = new JTable(mItens);
    JScrollPane spItens = new JScrollPane(tbItens);
    spItens.setBorder(new TitledBorder("O que foi vendido por usuário (itens por atendente)"));

    JPanel center = new JPanel(new GridLayout(2,1,10,10));
    center.add(spUser);
    center.add(spItens);

    JButton btExport = new JButton("Exportar PDF");
    JButton btnLimpar = new JButton("Limpar Relatório");
    JButton btFechar = new JButton("Fechar");
    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    bottom.add(btExport);
    bottom.add(btnLimpar);   // <-- adicionar no rodapé
    bottom.add(btFechar);

    d.add(top, BorderLayout.NORTH);
    d.add(center, BorderLayout.CENTER);
    d.add(bottom, BorderLayout.SOUTH);

    // Carregar dados
    Runnable load = () -> {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Double total = s.createQuery(
                    "select coalesce(sum(p.valor),0) from Pagamento p", Double.class
            ).getSingleResult();
            lbTotal.setText(money(total));

            // por registrador
            mUser.setRowCount(0);
            List<Object[]> porUser = s.createQuery(
                    "select coalesce(r.login,'(sem usuário)'), coalesce(sum(p.valor),0) " +
                    "from Pagamento p left join p.registrador r " +
                    "group by r.login order by 2 desc", Object[].class
            ).getResultList();
            for (Object[] row : porUser) {
                mUser.addRow(new Object[]{ (String)row[0], money((Double)row[1]) });
            }

            // itens por atendente
            mItens.setRowCount(0);
            List<Object[]> itens = s.createQuery(
                    "select ped.comanda.atendente.login, ped.produto.nome, " +
                    "sum(ped.quantidade), sum(ped.valorTotal) " +
                    "from Pedido ped " +
                    "group by ped.comanda.atendente.login, ped.produto.nome " +
                    "order by ped.comanda.atendente.login, ped.produto.nome",
                    Object[].class
            ).getResultList();
            for (Object[] r : itens) {
                Number qtd = (Number) r[2];
                mItens.addRow(new Object[]{ (String)r[0], (String)r[1], qtd.intValue(), money((Double)r[3]) });
            }
        } catch (Exception ex) { erro(ex); }
    };

    // guardar referências p/ reload automático ao fechar comanda
    this.relatoriosAberto = d;
    this.relatoriosReload = load;
    d.addWindowListener(new java.awt.event.WindowAdapter() {
        @Override public void windowClosed(java.awt.event.WindowEvent e) {
            relatoriosAberto = null;
            relatoriosReload = null;
        }
        @Override public void windowClosing(java.awt.event.WindowEvent e) { windowClosed(e); }
    });

    // Exportar PDF
    btExport.addActionListener(e -> {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("relatorio-vendas.pdf"));
        if (fc.showSaveDialog(d) == JFileChooser.APPROVE_OPTION) {
            try {
                exportarRelatorioPdf(fc.getSelectedFile().getAbsolutePath(),
                        lbTotal.getText(), mUser, mItens);
                JOptionPane.showMessageDialog(d, "PDF gerado com sucesso.");
            } catch (Exception ex) { erro(ex); }
        }
    });

    // Limpar relatório (apagando Pagamentos e Pedidos)
    btnLimpar.addActionListener(e -> {
    if (usuarioLogado.getPerfil() != PerfilUsuario.ADMIN) {
        JOptionPane.showMessageDialog(d, "Apenas o administrador pode limpar o relatório.");
        return;
    }

    // pede a senha
    JPasswordField pf = new JPasswordField();
    int opt = JOptionPane.showConfirmDialog(
            d, pf, "Confirme sua senha de administrador",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    if (opt != JOptionPane.OK_OPTION) return;

    String senhaDigitada = new String(pf.getPassword());

    // ==== DIAGNÓSTICO (temporário) ====
    System.out.println("[DBG] usuarioLogado.login = " + usuarioLogado.getLogin());
    System.out.println("[DBG] senhaDigitada len  = " + senhaDigitada.length());

    // busca SEMPRE no banco por login
    Usuario uDb;
    try (Session s = HibernateUtil.getSessionFactory().openSession()) {
        uDb = s.createQuery("from Usuario u where u.login = :l", Usuario.class)
                .setParameter("l", usuarioLogado.getLogin())
                .uniqueResult();
        if (uDb == null) {
            JOptionPane.showMessageDialog(d, "Usuário da sessão não foi encontrado no banco.");
            return;
        }
        System.out.println("[DBG] uDb.login   = " + uDb.getLogin());
        System.out.println("[DBG] uDb.perfil  = " + uDb.getPerfil());
        System.out.println("[DBG] hash len    = " + (uDb.getSenhaHash() == null ? 0 : uDb.getSenhaHash().length()));
        System.out.println("[DBG] hash prefix = " + (uDb.getSenhaHash() == null ? "null" : uDb.getSenhaHash().substring(0, 7)));

        if (uDb.getPerfil() != PerfilUsuario.ADMIN) {
            JOptionPane.showMessageDialog(d, "Apenas o administrador pode limpar o relatório.");
            return;
        }

        boolean ok = (uDb.getSenhaHash() != null)
                && org.mindrot.jbcrypt.BCrypt.checkpw(senhaDigitada, uDb.getSenhaHash());

        // teste extra: checa explicitamente vs "root123456" para isolar problema de digitação
        boolean okComRootFixo = (uDb.getSenhaHash() != null)
                && org.mindrot.jbcrypt.BCrypt.checkpw("root123456", uDb.getSenhaHash());

        System.out.println("[DBG] checkpw(digitada)   = " + ok);
        System.out.println("[DBG] checkpw('root123456') = " + okComRootFixo);

        if (!ok) {
            JOptionPane.showMessageDialog(d, "Senha incorreta!");
            return;
        }
    } catch (Exception ex) {
        erro(ex);
        return;
    }
    // ==== FIM DIAGNÓSTICO ====

    int confirm = JOptionPane.showConfirmDialog(
            d,
            "Tem certeza que deseja limpar o relatório? Esta ação não poderá ser desfeita.",
            "Confirmação", JOptionPane.YES_NO_OPTION);
    if (confirm != JOptionPane.YES_OPTION) return;

    try (Session session = HibernateUtil.getSessionFactory().openSession()) {
        Transaction tx = session.beginTransaction();
        session.createMutationQuery("delete from Pagamento").executeUpdate();
        session.createMutationQuery("delete from Pedido").executeUpdate();
        tx.commit();
        JOptionPane.showMessageDialog(d, "Relatório limpo com sucesso!");
        load.run();
    } catch (Exception ex) {
        erro(ex);
    }
});

    btFechar.addActionListener(e -> d.dispose());

    d.setSize(900, 600);
    d.setLocationRelativeTo(this);

    // carrega antes de abrir o diálogo
    load.run();
    d.setVisible(true);
}



    private void exportarRelatorioPdf(String file, String totalFmt,
                                      DefaultTableModel mUser, DefaultTableModel mItens) throws Exception {
        com.lowagie.text.Document doc = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4);
        com.lowagie.text.pdf.PdfWriter.getInstance(doc, new java.io.FileOutputStream(file));
        doc.open();

        com.lowagie.text.Font fTitle = new com.lowagie.text.Font(
                com.lowagie.text.Font.HELVETICA, 16, com.lowagie.text.Font.BOLD);
        com.lowagie.text.Font fH = new com.lowagie.text.Font(
                com.lowagie.text.Font.HELVETICA, 12, com.lowagie.text.Font.BOLD);
        com.lowagie.text.Font fN = new com.lowagie.text.Font(
                com.lowagie.text.Font.HELVETICA, 11, com.lowagie.text.Font.NORMAL);

        com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph("Relatório de Vendas", fTitle);
        title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
        title.setSpacingAfter(12);
        doc.add(title);

        doc.add(new com.lowagie.text.Paragraph("Total vendido: " + totalFmt, fH));
        doc.add(new com.lowagie.text.Paragraph("Gerado em: " + LocalDateTime.now(), fN));
        doc.add(com.lowagie.text.Chunk.NEWLINE);

        doc.add(new com.lowagie.text.Paragraph("Vendido por usuário (registrador do pagamento)", fH));
        com.lowagie.text.pdf.PdfPTable tUser = new com.lowagie.text.pdf.PdfPTable(2);
        tUser.setWidthPercentage(100);
        tUser.setSpacingBefore(5);
        tUser.addCell(headerCell("Usuário", fH));
        tUser.addCell(headerCell("Total", fH));
        for (int i=0;i<mUser.getRowCount();i++) {
            tUser.addCell(bodyCell(String.valueOf(mUser.getValueAt(i,0)), fN));
            tUser.addCell(bodyCell(String.valueOf(mUser.getValueAt(i,1)), fN));
        }
        doc.add(tUser);
        doc.add(com.lowagie.text.Chunk.NEWLINE);

        doc.add(new com.lowagie.text.Paragraph("O que foi vendido por usuário (itens por atendente)", fH));
        com.lowagie.text.pdf.PdfPTable tItens = new com.lowagie.text.pdf.PdfPTable(4);
        tItens.setWidthPercentage(100);
        tItens.setSpacingBefore(5);
        tItens.addCell(headerCell("Usuário", fH));
        tItens.addCell(headerCell("Produto", fH));
        tItens.addCell(headerCell("Qtd", fH));
        tItens.addCell(headerCell("Total", fH));
        for (int i=0;i<mItens.getRowCount();i++) {
            tItens.addCell(bodyCell(String.valueOf(mItens.getValueAt(i,0)), fN));
            tItens.addCell(bodyCell(String.valueOf(mItens.getValueAt(i,1)), fN));
            tItens.addCell(bodyCell(String.valueOf(mItens.getValueAt(i,2)), fN));
            tItens.addCell(bodyCell(String.valueOf(mItens.getValueAt(i,3)), fN));
        }
        doc.add(tItens);

        doc.close();
    }

    private com.lowagie.text.pdf.PdfPCell headerCell(String s, com.lowagie.text.Font f) {
        com.lowagie.text.pdf.PdfPCell c =
                new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(s, f));
        c.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
        c.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
        return c;
    }

    private com.lowagie.text.pdf.PdfPCell bodyCell(String s, com.lowagie.text.Font f) {
        com.lowagie.text.pdf.PdfPCell c =
                new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(s, f));
        c.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_LEFT);
        return c;
    }

    // =================== Utilitários & outros ===================
    private void carregarComandasAbertas(JComboBox<Comanda> combo) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Comanda> abertas = s.createQuery(
                    "from Comanda c where c.fechada = false order by c.id", Comanda.class).list();
            DefaultComboBoxModel<Comanda> m = new DefaultComboBoxModel<>();
            for (Comanda c : abertas) m.addElement(c);
            combo.setModel(m);
        } catch (Exception ex) { erro(ex); }
    }

    // Versão sem parâmetro só para compatibilidade (não quebra chamadas antigas)
    private void carregarComandasAbertas() {
        log("Comandas abertas recarregadas.");
    }

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

    private void cadastrarProdutoDialog() {
        JTextField tNome = new JTextField();
        JFormattedTextField tPreco =
                new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        JSpinner spEst = new JSpinner(new SpinnerNumberModel(0,0,100000,1));

        JPanel p = new JPanel(new GridLayout(0,2,6,6));
        p.add(new JLabel("Nome:"));    p.add(tNome);
        p.add(new JLabel("Preço:"));   p.add(tPreco);
        p.add(new JLabel("Estoque:")); p.add(spEst);

        int ok = JOptionPane.showConfirmDialog(this, p,
                "Cadastrar Produto", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Produto prod = novoProduto(
                    tNome.getText(),
                    parseValor(tPreco),
                    (int) spEst.getValue()
            );
            s.persist(prod);
            tx.commit();
            log("🍔 Produto #" + prod.getId() + " cadastrado: " + prod.getNome());
        } catch (Exception ex) { erro(ex); }
    }

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

    public static class ComandaRenderer extends BasicComboBoxRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Comanda c) {
                String cliente = c.getClienteNome();
                String mesa = c.getMesa();
                String label = "#" + c.getId();
                if (cliente != null && !cliente.isBlank()) label += " - " + cliente;
                if (mesa != null && !mesa.isBlank()) label += " | Mesa " + mesa;
                setText(label);
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