package br.com.cr_system;

import org.hibernate.Session;
import org.hibernate.Transaction;

public class AuthService {

    /** Cria admin padrão se não existir nenhum usuário (agora com BCrypt). */
    public static void ensureDefaultAdmin() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long count = s.createQuery("select count(u) from Usuario u", Long.class).getSingleResult();
            if (count == 0) {
                Transaction tx = s.beginTransaction();
                Usuario admin = new Usuario();
                admin.setLogin("admin");
                admin.setNome("Administrador");
                admin.setPerfil(PerfilUsuario.ADMIN);
                admin.setSenhaHash(SenhaUtil.hashSenha("admin123")); 
                s.persist(admin);
                tx.commit();
                System.out.println("[Auth] admin criado: login=admin / senha=admin123");
            }
        }
    }

    /** Retorna o usuário autenticado ou null se falhar. */
    public static Usuario authenticate(String login, String senha) {
        if (login == null || senha == null) return null;
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Usuario u = s.createQuery("from Usuario u where u.login = :l", Usuario.class)
                    .setParameter("l", login.trim())
                    .uniqueResult();
            if (u == null) return null;
            return SenhaUtil.verificar(senha, u.getSenhaHash()) ? u : null;
        }
    }

    public static void changePassword(Long userId, String novaSenha) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Usuario u = s.get(Usuario.class, userId);
            if (u != null) {
                u.setSenhaHash(SenhaUtil.hashSenha(novaSenha));
            }
            tx.commit();
        }
    }
}