package br.com.cr_system.ui;

import br.com.cr_system.Comanda;
import br.com.cr_system.Produto;

import javax.swing.*;
import java.util.List;

public interface ComandasView {
    void log(String s);
    void alerta(String s);

    // preencher combos da tela principal
    void setComandasAbertas(List<Comanda> abertas);
    void setProdutos(List<Produto> produtos);

    // acesso a campos da tela principal (quando necessário)
    JComboBox<?> getCbComandaPagto();
    JComboBox<?> getCbTipoPadrao();
    JFormattedTextField getTxtValor();
    JComboBox<?> getCbComandaFechar();
}