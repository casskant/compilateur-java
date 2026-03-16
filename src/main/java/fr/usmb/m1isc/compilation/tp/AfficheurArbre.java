package fr.usmb.m1isc.compilation.tp;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Afficheur d'arbre syntaxique (AST) en mode texte.
 *
 * Produit un affichage visuel de l'arbre et l'enregistre dans le dossier :
 *
 *   ;
 *   ├── LET x
 *   │   └── INPUT
 *   ├── LET y
 *   │   └── INPUT
 *   └── WHILE
 *       ├── >
 *       │   ├── 0
 *       │   └── b
 *       └── ...
 */
public class AfficheurArbre {

    public void afficher(Arbre arbre) {
        StringBuilder sb = new StringBuilder();
        construire(arbre, sb, "", true);
        System.out.println(sb);
    }

    public void afficherDansFichier(Arbre arbre, String chemin) {
        StringBuilder sb = new StringBuilder();
        construire(arbre, sb, "", true);
        try (PrintWriter writer = new PrintWriter(new FileWriter(chemin))) {
            writer.print(sb);
            System.out.println("Arbre sauvegardé dans : " + chemin);
        } catch (IOException e) {
            System.err.println("Erreur écriture fichier : " + e.getMessage());
        }
    }

    /**
     * @param arbre      Le nœud courant
     * @param sb         Accumulateur de texte
     * @param prefixe    Barres verticales héritées des parents
     * @param estDernier Vrai si ce nœud est le dernier enfant → connecteur └── sinon ├──
     */
    private void construire(Arbre arbre, StringBuilder sb, String prefixe, boolean estDernier) {

        String connecteur      = estDernier ? "└── " : "├── ";
        String prefixeEnfants  = prefixe + (estDernier ? "    " : "│   ");

        if (arbre == null) {
            sb.append(prefixe).append(connecteur).append("(null)\n");
            return;
        }

        if (arbre instanceof ArbreEntier e) {
            sb.append(prefixe).append(connecteur).append(e.valeur).append("\n");

        } else if (arbre instanceof ArbreIdent id) {
            sb.append(prefixe).append(connecteur).append(id.nom).append("\n");

        } else if (arbre instanceof ArbreLet let) {
            sb.append(prefixe).append(connecteur).append("LET ").append(let.nomVariable).append("\n");
            construire(let.valeur, sb, prefixeEnfants, true);

        } else if (arbre instanceof ArbreUnaire u) {
            sb.append(prefixe).append(connecteur).append(u.operateur).append("\n");
            construire(u.expression, sb, prefixeEnfants, true);

        } else if (arbre instanceof ArbreBinaire bin) {
            sb.append(prefixe).append(connecteur).append(bin.operateur).append("\n");
            construire(bin.gauche, sb, prefixeEnfants, false); // ├──
            construire(bin.droite, sb, prefixeEnfants, true);  // └──

        } else if (arbre instanceof ArbreLambda lambda) {
            sb.append(prefixe).append(connecteur).append("LAMBDA ").append(lambda.parametres).append("\n");
            construire(lambda.corps, sb, prefixeEnfants, true);

        } else if (arbre instanceof ArbreAppel appel) {
            sb.append(prefixe).append(connecteur).append("APPEL ").append(appel.nomFonction).append("\n");
            for (int i = 0; i < appel.arguments.size(); i++) {
                construire(appel.arguments.get(i), sb, prefixeEnfants, i == appel.arguments.size() - 1);
            }
        }
    }
}
