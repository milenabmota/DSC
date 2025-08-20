package br.com.cr_system;

import org.mindrot.jbcrypt.BCrypt;

public class SenhaUtil {
    public static String hashSenha(String senhaPlana) {
        return BCrypt.hashpw(senhaPlana, BCrypt.gensalt(12));
    }
    public static boolean verificar(String senhaPlana, String hash) {
        if (senhaPlana == null || hash == null) return false;
        return BCrypt.checkpw(senhaPlana, hash);
    }
}