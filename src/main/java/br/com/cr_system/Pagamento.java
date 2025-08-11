package br.com.cr_system;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagamentos")
public class Pagamento {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  private Comanda comanda;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoPagamento tipo;

  @Column(nullable = false)
  private Double valor;

  @Column(nullable = false)
  private LocalDateTime dataHora = LocalDateTime.now();

  // getters/setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public Comanda getComanda() { return comanda; }
  public void setComanda(Comanda comanda) { this.comanda = comanda; }

  public TipoPagamento getTipo() { return tipo; }
  public void setTipo(TipoPagamento tipo) { this.tipo = tipo; }

  public Double getValor() { return valor; }
  public void setValor(Double valor) { this.valor = valor; }

  public LocalDateTime getDataHora() { return dataHora; }
  public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }
}