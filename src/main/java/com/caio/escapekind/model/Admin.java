package com.caio.escapekind.model;

import jakarta.persistence.*;

/**
 * Entidade que representa um administrador do sistema.
 * A password é sempre armazenada como hash BCrypt — nunca em texto simples.
 */
@Entity
@Table(name = "admins")
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    /**
     * Hash BCrypt da password. Gerado via BCryptPasswordEncoder.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    public Admin() {}

    public Admin(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
