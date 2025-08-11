package br.com.cr_system;
import jakarta.persistence.*;

@Entity @Table(name="pedidos")
public class Pedido {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;

  @ManyToOne(optional=false) private Comanda comanda;
  @ManyToOne(optional=false) private Produto produto;

  @Column(nullable=false) private Integer quantidade;
  @Column(nullable=false) private Double valorTotal;

  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public Comanda getComanda(){return comanda;} public void setComanda(Comanda c){this.comanda=c;}
  public Produto getProduto(){return produto;} public void setProduto(Produto p){this.produto=p;}
  public Integer getQuantidade(){return quantidade;} public void setQuantidade(Integer q){this.quantidade=q;}
  public Double getValorTotal(){return valorTotal;} public void setValorTotal(Double v){this.valorTotal=v;}
}