package fr.usmb.m1isc.compilation.tp;

import java.io.FileReader;
import java_cup.runtime.Symbol;

public class Main {
    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.err.println("Usage: java Main <fichier.lada>");
            System.exit(1);
        }

        FileReader fichier = new FileReader(args[0]);

        // Lexer généré par JFlex
        LexicalAnalyzer lexer = new LexicalAnalyzer(fichier);

        // Parser généré par CUP → retourne l'arbre
        parser p = new parser(lexer);
        Symbol result = p.parse();
        Arbre arbre = (Arbre) result.value;

        if (arbre == null) {
            System.err.println("Erreur : arbre vide.");
            System.exit(1);
        }

        // ── Affichage de l'arbre ──────────────────────────────────────────
        AfficheurArbre afficheur = new AfficheurArbre();
        System.out.println("=== ARBRE SYNTAXIQUE ===");
        afficheur.afficher(arbre);                            // dans le terminal
        afficheur.afficherDansFichier(arbre, "arbre.txt");    // dans un fichier

        // ── Génération du code assembleur ─────────────────────────────────
        System.out.println("=== CODE ASSEMBLEUR ===");
        GenerateurCode gen = new GenerateurCode();
        System.out.println(gen.generer(arbre));
    }
}