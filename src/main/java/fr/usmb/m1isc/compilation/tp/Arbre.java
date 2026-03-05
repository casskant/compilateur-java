package fr.usmb.m1isc.compilation.tp;

import java.util.List;
import java.util.ArrayList;

// ── Classe racine abstraite ───────────────────────────────────────────────────
public abstract class Arbre {
    public abstract String toString();
}

// ── Nombre entier (ex: 42) ────────────────────────────────────────────────────
class ArbreEntier extends Arbre {
    int valeur;
    public ArbreEntier(int v) { this.valeur = v; }
    public String toString() { return String.valueOf(valeur); }
}

// ── Identifiant ou mot-clé feuille (ex: prixHt, INPUT, NIL) ──────────────────
class ArbreIdent extends Arbre {
    String nom;
    public ArbreIdent(String n) { this.nom = n; }
    public String toString() { return nom; }
}

// ── Opération binaire (+, -, *, /, %, ;, AND, OR, WHILE, IF...) ──────────────
class ArbreBinaire extends Arbre {
    String operateur;
    Arbre gauche, droite;
    public ArbreBinaire(String op, Arbre g, Arbre d) {
        this.operateur = op;
        this.gauche = g;
        this.droite = d;
    }
    public String toString() {
        return "(" + operateur + " " + gauche.toString() + " " + droite.toString() + ")";
    }
}

// ── Opération unaire (NOT, -, OUTPUT) ────────────────────────────────────────
class ArbreUnaire extends Arbre {
    String operateur;
    Arbre expression;
    public ArbreUnaire(String op, Arbre e) {
        this.operateur = op;
        this.expression = e;
    }
    public String toString() {
        return "(" + operateur + " " + expression.toString() + ")";
    }
}

// ── Déclaration LET (ex: let x = 42) ─────────────────────────────────────────
class ArbreLet extends Arbre {
    String nomVariable;
    Arbre valeur;
    public ArbreLet(String nom, Arbre v) {
        this.nomVariable = nom;
        this.valeur = v;
    }
    public String toString() {
        return "(LET " + nomVariable + " " + valeur.toString() + ")";
    }
}

// ── Définition lambda (ex: lambda (a, b) (a + b)) ────────────────────────────
class ArbreLambda extends Arbre {
    List<String> parametres;
    Arbre corps;
    public ArbreLambda(List<String> p, Arbre c) {
        this.parametres = p;
        this.corps = c;
    }
    public String toString() {
        return "(LAMBDA " + parametres + " " + corps.toString() + ")";
    }
}

// ── Appel de fonction (ex: pgcd(x, y)) ───────────────────────────────────────
class ArbreAppel extends Arbre {
    String nomFonction;
    List<Arbre> arguments;
    public ArbreAppel(String nom, List<Arbre> args) {
        this.nomFonction = nom;
        this.arguments = args;
    }
    public String toString() {
        return "(APPEL " + nomFonction + " " + arguments.toString() + ")";
    }
}