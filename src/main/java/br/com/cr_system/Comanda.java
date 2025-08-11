package br.com.cr_system;

import jakarta.persistence.*;

@Entity
@Table(name = "comandas")
public class Comanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // relacionamento simples com usuário (atendente)
    @ManyToOne(optional = false)
    @JoinColumn(name = "atendente_id")
    private Usuario atendente;

    @Column(name = "clienteNome", length = 120)
    private String clienteNome;

    // >>> NOVO CAMPO: mesa
    @Column(name = "mesa", length = 30)
    private String mesa;

    // pode ser definido só no fechamento/pagamento
    @Enumerated(EnumType.STRING)
    @Column(name = "tipoPagamento", nullable = true)
    private TipoPagamento tipoPagamento;

    @Column(nullable = false)
    private Boolean fechada = false;

    // ===== getters/setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getAtendente() { return atendente; }
    public void setAtendente(Usuario atendente) { this.atendente = atendente; }

    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }

    public String getMesa() { return mesa; }
    public void setMesa(String mesa) { this.mesa = mesa; }

    public TipoPagamento getTipoPagamento() { return tipoPagamento; }
    public void setTipoPagamento(TipoPagamento tipoPagamento) { this.tipoPagamento = tipoPagamento; }

    public Boolean getFechada() { return fechada; }
    public void setFechada(Boolean fechada) { this.fechada = fechada; }
}