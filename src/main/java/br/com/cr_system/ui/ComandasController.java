package br.com.cr_system.ui;

import br.com.cr_system.*;
import org.hibernate.Session;
import org.hibernate.Transaction;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.List;

public class ComandasController {
    private final ComandasView view;

    public ComandasController(ComandasView view) {
        this.view = view;
    }

    // ======== CARREGAR DADOS ========
    public void carregarComandasAbertas() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Comanda> abertas = s.createQuery(
                "from Comanda c where c.fechada = false order by c.id", Comanda.class
            ).getResultList();
            view.setComandasAbertas(abertas);
        } catch (Exception ex) {
            view.alerta("Erro ao carregar comandas: " + ex.getMessage());
        }
    }

    public void carregarProdutos(String filtroLower) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Produto> list;
            if (filtroLower != null && !filtroLower.isBlank()) {
                list = s.createQuery(
                    "from Produto p where lower(p.nome) like :q order by p.nome", Produto.class)
                    .setParameter("q","%"+filtroLower.toLowerCase()+"%").list();
            } else {
                list = s.createQuery("from Produto order by nome", Produto.class).list();
            }
            if (list.isEmpty() && (filtroLower == null || filtroLower.isBlank())) {
                Transaction tx = s.beginTransaction();
                s.persist(novoProduto("X-Burger", 18.0, 50));
                s.persist(novoProduto("Refrigerante Lata", 7.0, 100));
                s.persist(novoProduto("Porção de Batata", 22.0, 40));
                tx.commit();
                list = s.createQuery("from Produto order by nome", Produto.class).list();
                view.log("📦 Produtos de exemplo criados.");
            }
            view.setProdutos(list);
        } catch (Exception ex) {
            view.alerta("Erro ao carregar produtos: " + ex.getMessage());
        }
    }

    // ======== AÇÕES ========
    // Agora sem tipo de pagamento: definido apenas em Registrar Pagamento / Fechar
    public void abrirComanda(String cliente) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();

            var atendente = s.createQuery("from Usuario where login = :l", Usuario.class)
                .setParameter("l", "maria").uniqueResult();
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
            c.setClienteNome((cliente == null || cliente.isBlank()) ? null : cliente.trim());
            c.setTipoPagamento(null); // definido depois
            c.setFechada(false);
            s.persist(c);

            tx.commit();
            view.log("🧾 Comanda #"+c.getId()+" aberta por "+atendente.getNome()
                    + (c.getClienteNome()!=null?(" para "+c.getClienteNome()):""));
            carregarComandasAbertas();
        } catch (Exception ex) {
            view.alerta("Erro ao abrir comanda: " + ex.getMessage());
        }
    }

    public void cadastrarProduto(String nome, double preco, int estoque) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Produto prod = novoProduto(nome, preco, estoque);
            s.persist(prod);
            tx.commit();
            view.log("🍔 Produto #"+prod.getId()+" cadastrado: "+prod.getNome());
            carregarProdutos(""); // refresh
        } catch (Exception ex) {
            view.alerta("Erro ao cadastrar produto: " + ex.getMessage());
        }
    }

    public void registrarPagamento(long comandaId, TipoPagamento tipo, double valor) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Comanda com = s.get(Comanda.class, comandaId);
            Transaction tx = s.beginTransaction();
            Pagamento pg = new Pagamento();
            pg.setComanda(com);
            pg.setTipo(tipo);
            pg.setDataHora(LocalDateTime.now());
            pg.setValor(valor);
            s.persist(pg);
            tx.commit();
            view.log("💳 Pagamento #"+pg.getId()+" de R$ "+String.format("%.2f",valor)+" via "+tipo);
            mostrarTotais(comandaId);
        } catch (Exception ex) {
            view.alerta("Erro ao registrar pagamento: " + ex.getMessage());
        }
    }

    public void fecharComanda(long comandaId, TipoPagamento tipo) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Comanda c = s.get(Comanda.class, comandaId);

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
                c.setTipoPagamento(tipo);
                c.setFechada(true);
                view.log("✅ Comanda #"+c.getId()+" fechada. Pagamento final R$ "
                        + String.format("%.2f", restante)+" via "+tipo);
            } else {
                c.setTipoPagamento(tipo);
                c.setFechada(true);
                view.log("✅ Comanda #"+c.getId()+" fechada sem valor restante. Tipo: "+tipo);
            }
            tx.commit();
            carregarComandasAbertas();
        } catch (Exception ex) {
            view.alerta("Erro ao fechar comanda: " + ex.getMessage());
        }
    }

    public void adicionarPedido(long comandaId, long produtoId, int qtd) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Comanda com = s.get(Comanda.class, comandaId);
            if (com == null || Boolean.TRUE.equals(com.getFechada())) {
                view.alerta("Comanda inválida/fechada."); return;
            }
            Produto p = s.get(Produto.class, produtoId);
            Transaction tx = s.beginTransaction();
            Pedido ped = new Pedido();
            ped.setComanda(com);
            ped.setProduto(p);
            ped.setQuantidade(qtd);
            ped.setValorTotal(p.getPreco() * qtd);
            s.persist(ped);
            tx.commit();
            view.log("📝 Pedido #"+ped.getId()+" criado na comanda #"+com.getId());
            mostrarTotais(com.getId());
        } catch (Exception ex) {
            view.alerta("Erro ao adicionar pedido: " + ex.getMessage());
        }
    }

    // ======== UTIL ========
    private void mostrarTotais(Long comandaId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Double total = s.createQuery(
                "select coalesce(sum(p.valorTotal),0) from Pedido p where p.comanda.id = :id", Double.class)
                .setParameter("id", comandaId).getSingleResult();
            Double pagos = s.createQuery(
                "select coalesce(sum(pg.valor),0) from Pagamento pg where pg.comanda.id = :id", Double.class)
                .setParameter("id", comandaId).getSingleResult();
            double rest = total - pagos;
            view.log(String.format("📊 Comanda #%d | Total: R$ %.2f | Pago: R$ %.2f | Restante: R$ %.2f",
                comandaId, total, pagos, rest));
        }
    }

    private static Produto novoProduto(String nome, double preco, int estoque) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setPreco(preco);
        p.setEstoque(estoque);
        return p;
    }
}