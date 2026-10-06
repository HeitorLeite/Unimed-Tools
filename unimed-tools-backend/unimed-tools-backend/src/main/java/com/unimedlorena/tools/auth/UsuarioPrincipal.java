package com.unimedlorena.tools.auth;

import java.util.Set;

/** Identidade obtida exclusivamente de uma sessão opaca validada no servidor. */
public record UsuarioPrincipal(
  long id,
  String nome,
  String login,
  String email,
  String perfil,
  boolean deveTrocarSenha,
  Set<String> permissoes
) {}
