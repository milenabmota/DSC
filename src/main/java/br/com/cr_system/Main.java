package br.com.cr_system;

import org.hibernate.Session;

public class Main {
  public static void main(String[] args) {
    try (Session s = HibernateUtil.getSessionFactory().openSession()) {
      System.out.println("✅ Conectado ao MySQL via Hibernate.");

      // 1) Criar um usuário (ATENDENTE)
      s.beginTransaction();
      Usuario u = new Usuario();
      u.setNome("Maria Silva");
      u.setLogin("maria");
      u.setSenhaHash("123456"); // TODO: trocar por hash (BCrypt) depois
      u.setPerfil(PerfilUsuario.ATENDENTE);
      u.setTelefone("3899999-0000");
      s.persist(u);
      s.getTransaction().commit();
      System.out.println("👤 Usuario #" + u.getId() + " criado: " + u.getNome() + " (" + u.getPerfil() + ")");

      // 2) Criar um produto
      s.beginTransaction();
      Produto prod = new Produto();
      prod.setNome("X-Burger");
      prod.setPreco(18.0);
      prod.setEstoque(20);
      s.persist(prod);
      s.getTransaction().commit();
      System.out.println("🍔 Produto #" + prod.getId() + " cadastrado: " + prod.getNome());

      // 3) Abrir comanda (com atendente e tipo de pagamento)
      s.beginTransaction();
      Comanda com = new Comanda();
      com.setFechada(false);
      com.setTipoPagamento(TipoPagamento.PIX);
      com.setAtendente(u); // vínculo com o atendente que abriu
      s.persist(com);
      s.getTransaction().commit();
      System.out.println("🧾 Comanda #" + com.getId() + " aberta por " + u.getNome() + " (pagamento: " + com.getTipoPagamento() + ")");

      // 4) Lançar pedido (2x X-Burger)
      s.beginTransaction();
      Pedido ped = new Pedido();
      ped.setComanda(com);
      ped.setProduto(prod);
      ped.setQuantidade(2);
      ped.setValorTotal(prod.getPreco() * ped.getQuantidade()); // snapshot
      s.persist(ped);
      s.getTransaction().commit();
      System.out.println("📝 Pedido #" + ped.getId() + " criado na comanda #" + com.getId());

      // 5) Registrar pagamento parcial
      s.beginTransaction();
      Pagamento pay = new Pagamento();
      pay.setComanda(com);
      pay.setTipo(TipoPagamento.PIX);
      pay.setValor(20.0);
      s.persist(pay);
      s.getTransaction().commit();
      System.out.println("💳 Pagamento #" + pay.getId() + " de R$ " + pay.getValor() + " via " + pay.getTipo());

      // 6) Totais: soma pedidos, soma pagamentos e saldo
      Double totalPedidos = s.createQuery(
          "select coalesce(sum(p.valorTotal),0) from Pedido p where p.comanda.id = :id", Double.class)
          .setParameter("id", com.getId())
          .getSingleResult();

      Double totalPago = s.createQuery(
          "select coalesce(sum(pg.valor),0) from Pagamento pg where pg.comanda.id = :id", Double.class)
          .setParameter("id", com.getId())
          .getSingleResult();

      Double restante = totalPedidos - totalPago;
      System.out.println("📊 Comanda #" + com.getId() +
          " | Total: R$ " + totalPedidos +
          " | Pago: R$ " + totalPago +
          " | Restante: R$ " + restante);

      // 7) Consulta: comandas abertas por atendente
      var abertasDoAtendente = s.createQuery(
          "from Comanda c where c.atendente.id = :uid and c.fechada = false", Comanda.class)
          .setParameter("uid", u.getId())
          .getResultList();
      System.out.println("🔎 Comandas abertas por " + u.getNome() + ": " + abertasDoAtendente.size());

      // 8) (Opcional) Fechar comanda se quitada
      if (restante <= 0.00001) {
        s.beginTransaction();
        com.setFechada(true);
        s.merge(com);
        s.getTransaction().commit();
        System.out.println("✅ Comanda #" + com.getId() + " FECHADA.");
      } else {
        System.out.println("⏳ Comanda #" + com.getId() + " ainda em aberto.");
      }

    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      HibernateUtil.shutdown();
    }
  }
}