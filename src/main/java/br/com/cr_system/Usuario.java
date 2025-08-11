package br.com.cr_system;

import jakarta.persistence.*;

@Entity
@Table(name = "usuarios")
public class Usuario {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable=false, length=100)
  private String nome;

  @Column(nullable=false, unique=true, length=60)
  private String login;

  @Column(nullable=false, length=120)
  private String senhaHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable=false, length=12)
  private PerfilUsuario perfil;

  @Column(length=20)
  private String telefone;

  // getters/setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getNome() { return nome; }
  public void setNome(String nome) { this.nome = nome; }
  public String getLogin() { return login; }
  public void setLogin(String login) { this.login = login; }
  public String getSenhaHash() { return senhaHash; }
  public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
  public PerfilUsuario getPerfil() { return perfil; }
  public void setPerfil(PerfilUsuario perfil) { this.perfil = perfil; }
  public String getTelefone() { return telefone; }
  public void setTelefone(String telefone) { this.telefone = telefone; }
}