package fr.usmb.m1isc.compilation.tp;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Générateur de code assembleur pour le langage λ-ada.
 *
 * Principe général :
 *   - On parcourt l'arbre syntaxique (AST) de manière récursive.
 *   - Chaque nœud de l'arbre produit une suite d'instructions assembleur.
 *   - Les résultats intermédiaires des expressions sont toujours placés dans eax.
 *   - La pile (push/pop) est utilisée pour sauvegarder les opérandes gauches
 *     lors de l'évaluation des opérations binaires.
 *
 * Structure du code produit :
 *   DATA SEGMENT  → déclarations des variables (DD = Double Word = 32 bits)
 *   CODE SEGMENT  → instructions assembleur
 */
public class GenerateurCode {

    // Accumulateur pour les déclarations de variables (segment DATA)
    private StringBuilder dataSegment = new StringBuilder();

    // Accumulateur pour les instructions assembleur (segment CODE)
    private StringBuilder codeSegment = new StringBuilder();

    // Ensemble des variables déjà déclarées dans le segment DATA
    // (évite les doublons si une variable est réaffectée avec LET)
    private Set<String> variablesDéclarées = new HashSet<>();

    // Compteur pour générer des labels uniques (debut_while_0, fin_while_1, etc.)
    // Chaque structure de contrôle (while, if) incrémente ce compteur.
    private int compteurLabel = 0;

    // ─── Point d'entrée ──────────────────────────────────────────────────────

    /**
     * Méthode principale appelée par Main.java.
     * Lance la génération récursive puis assemble le code final.
     *
     * @param arbre La racine de l'arbre syntaxique produit par CUP
     * @return Le code assembleur complet sous forme de String
     */
    public String generer(Arbre arbre) {
        genererInstruction(arbre);
        return "DATA SEGMENT\n"
             + dataSegment
             + "DATA ENDS\n"
             + "CODE SEGMENT\n"
             + codeSegment
             + "CODE ENDS\n";
    }

    // ─── Niveau instruction ──────────────────────────────────────────────────

    /**
     * Génère le code pour un nœud de type "instruction" :
     *   - ArbreLet     : déclaration/affectation  (let x = expr)
     *   - ArbreUnaire  : affichage                (output expr)
     *   - ArbreBinaire : séquence ";", while, if, ou expression seule
     *
     * Invariant : après l'appel, le résultat éventuel est dans eax.
     */
    private void genererInstruction(Arbre arbre) {

        if (arbre instanceof ArbreLet let) {
            // ── LET : déclaration et affectation d'une variable ──────────────
            // Si la variable n'a pas encore été déclarée, on l'ajoute au DATA SEGMENT
            if (!variablesDéclarées.contains(let.nomVariable)) {
                dataSegment.append("\t").append(let.nomVariable).append(" DD\n");
                variablesDéclarées.add(let.nomVariable);
            }

            // Cas spécial : "let x = input"
            // INPUT est représenté comme ArbreIdent("INPUT") par le parser CUP.
            // On génère "in eax" (lecture depuis l'entrée standard) au lieu de "mov eax, INPUT".
            if (let.valeur instanceof ArbreIdent id && id.nom.equalsIgnoreCase("INPUT")) {
                codeSegment.append("\tin eax\n");                                       // lire la valeur saisie
                codeSegment.append("\tmov ").append(let.nomVariable).append(", eax\n"); // stocker dans la variable
            } else {
                // Cas général : évaluer l'expression → résultat dans eax
                genererExpression(let.valeur);
                codeSegment.append("\tmov ").append(let.nomVariable).append(", eax\n"); // stocker dans la variable
            }

        } else if (arbre instanceof ArbreUnaire u && u.operateur.equals("OUTPUT")) {
            // ── OUTPUT : affichage d'une valeur ──────────────────────────────
            // Évaluer l'expression → résultat dans eax, puis afficher avec "out"
            genererExpression(u.expression);
            codeSegment.append("\tout eax\n");

        } else if (arbre instanceof ArbreBinaire bin) {
            switch (bin.operateur) {

                case ";" -> {
                    // ── SÉQUENCE : exécuter gauche puis droite ────────────────
                    // Le ";" sépare deux instructions dans le langage source.
                    // Ex : "let a=1 ; let b=2" → générer les deux l'une après l'autre
                    genererInstruction(bin.gauche);
                    genererInstruction(bin.droite);
                }

                case "WHILE" -> genererWhile(bin); // déléguer à genererWhile()
                case "IF"    -> genererIf(bin);    // déléguer à genererIf()

                default -> genererExpression(arbre); // expression utilisée comme instruction
            }
        } else {
            genererExpression(arbre);
        }
    }

    // ─── WHILE ───────────────────────────────────────────────────────────────

    /**
     * Génère le code pour une boucle while.
     * Structure de l'arbre : ArbreBinaire("WHILE", condition, corps)
     *
     * Code assembleur produit :
     *   debut_while_N:
     *     <évaluation condition> → eax = 1 (vrai) ou 0 (faux)
     *     jz fin_while_N         → si faux (eax==0), sortir de la boucle
     *     <corps de la boucle>
     *     jmp debut_while_N      → retourner au début
     *   fin_while_N:
     */
    private void genererWhile(ArbreBinaire bin) {
        int id = compteurLabel++; // id unique pour cette boucle

        codeSegment.append("debut_while_").append(id).append(":\n");

        // Évaluer la condition → eax = 1 (vrai) ou 0 (faux)
        genererCondition(bin.gauche, id);

        // jz = "jump if zero" : si eax == 0 (condition fausse), sauter à fin_while
        codeSegment.append("\tjz fin_while_").append(id).append("\n");

        // Générer le corps de la boucle
        genererInstruction(bin.droite);

        // Retourner au début pour réévaluer la condition
        codeSegment.append("\tjmp debut_while_").append(id).append("\n");
        codeSegment.append("fin_while_").append(id).append(":\n");
    }

    // ─── IF ──────────────────────────────────────────────────────────────────

    /**
     * Génère le code pour un if/then/else.
     * Structure de l'arbre :
     *   ArbreBinaire("IF", condition, ArbreBinaire("THEN_ELSE", alors, sinon))
     *
     * Code assembleur produit (avec else) :
     *   <évaluation condition> → eax = 1 ou 0
     *   jz fin_then_N          → si faux, sauter au else
     *   <branche THEN>
     *   jmp fin_if_N           → sauter après le else
     *   fin_then_N:
     *   <branche ELSE>
     *   fin_if_N:
     *
     * Sans else :
     *   jz fin_then_N
     *   <branche THEN>
     *   fin_then_N:
     */
    private void genererIf(ArbreBinaire bin) {
        int id = compteurLabel++; // id unique pour ce if

        // Évaluer la condition → eax = 1 (vrai) ou 0 (faux)
        genererCondition(bin.gauche, id);

        // Si condition fausse (eax==0), sauter à fin_then (= début du else ou fin du if)
        codeSegment.append("\tjz fin_then_").append(id).append("\n");

        if (bin.droite instanceof ArbreBinaire tb && tb.operateur.equals("THEN_ELSE")) {
            // IF avec ELSE
            genererInstruction(tb.gauche);                                        // branche THEN
            codeSegment.append("\tjmp fin_if_").append(id).append("\n");          // sauter après le ELSE
            codeSegment.append("fin_then_").append(id).append(":\n");             // label début ELSE
            genererInstruction(tb.droite);                                        // branche ELSE
            codeSegment.append("fin_if_").append(id).append(":\n");              // label fin IF
        } else {
            // IF sans ELSE
            genererInstruction(bin.droite);
            codeSegment.append("fin_then_").append(id).append(":\n");
        }
    }

    // ─── Condition → eax = 1 (vrai) ou 0 (faux) ─────────────────────────────

    /**
     * Génère le code pour évaluer une condition booléenne.
     * Le résultat est placé dans eax : 1 = vrai, 0 = faux.
     *
     * Gère :
     *   - AND avec court-circuit : si gauche est faux, ne pas évaluer droite
     *   - OR  avec court-circuit : si gauche est vrai, ne pas évaluer droite
     *   - Toute comparaison (<, <=, >, >=, =, !=) → délégué à genererComparaison()
     *
     * @param id Le même id que la structure englobante (while/if) pour les labels
     */
    private void genererCondition(Arbre arbre, int id) {
        if (!(arbre instanceof ArbreBinaire bin)) {
            // Cas simple : expression booléenne déjà dans eax (ex: variable booléenne)
            genererExpression(arbre);
            return;
        }

        switch (bin.operateur) {
            case "AND" -> {
                // Court-circuit AND : si gauche == 0 (faux), résultat = 0 sans évaluer droite
                genererCondition(bin.gauche, id);
                codeSegment.append("\tjz fin_and_").append(id).append("\n"); // gauche faux → sortir
                genererCondition(bin.droite, id);
                codeSegment.append("fin_and_").append(id).append(":\n");
            }
            case "OR" -> {
                // Court-circuit OR : si gauche != 0 (vrai), résultat = 1 sans évaluer droite
                genererCondition(bin.gauche, id);
                codeSegment.append("\tjnz fin_or_").append(id).append("\n"); // gauche vrai → sortir
                genererCondition(bin.droite, id);
                codeSegment.append("fin_or_").append(id).append(":\n");
            }
            default -> genererComparaison(bin, id); // <, <=, >, >=, =, !=
        }
    }

    // ─── Comparaison → eax = 1 ou 0 ─────────────────────────────────────────

    /**
     * Génère le code pour une comparaison binaire (ex: 0 < b).
     *
     * Stratégie :
     *   1. Évaluer l'opérande gauche → eax, sauvegarder sur la pile
     *   2. Évaluer l'opérande droite → eax
     *   3. Récupérer l'opérande gauche dans ebx
     *   4. Calculer ebx - eax (gauche - droite)
     *   5. Sauter sur le label "vrai" selon le signe du résultat
     *   6. Si pas de saut : eax = 0 (faux), sinon eax = 1 (vrai)
     *
     * Note importante sur le JFlex :
     *   Dans le fichier .jflex, "<" est mappé sur sym.GT et "<=" sur sym.GTE.
     *   Donc dans l'arbre, "0 < b" devient ArbreBinaire(">", 0, b).
     *   On traite donc ">" comme "<" et ">=" comme "<=" pour corriger ce décalage.
     */
    private void genererComparaison(ArbreBinaire bin, int id) {
        // Étape 1 : évaluer l'opérande gauche, sauvegarder sur la pile
        genererExpression(bin.gauche);
        codeSegment.append("\tpush eax\n");

        // Étape 2 : évaluer l'opérande droite → eax
        genererExpression(bin.droite);

        // Étape 3 : récupérer l'opérande gauche dans ebx
        codeSegment.append("\tpop ebx\n");

        // Étape 4 : ebx = gauche - droite
        // Si gauche < droite → ebx < 0 → flag négatif → jl saute
        codeSegment.append("\tsub ebx, eax\n");

        String labelVrai = "vrai_jl_" + id;
        String labelFin  = "fin_jl_"  + id;

        // Étape 5 : choisir le saut conditionnel selon l'opérateur
        // ">" et "<" sont traités identiquement (voir note JFlex ci-dessus)
        String saut = switch (bin.operateur) {
            case "<", ">"   -> "jl";   // ebx < 0  ⟺  gauche < droite
            case "<=", ">=" -> "jle";  // ebx <= 0 ⟺  gauche <= droite
            case "="        -> "je";   // ebx == 0 ⟺  gauche == droite
            case "!="       -> "jne";  // ebx != 0 ⟺  gauche != droite
            default         -> "jl";
        };

        // Étape 6 : produire 0 ou 1 dans eax
        codeSegment.append("\t").append(saut).append(" ").append(labelVrai).append("\n");
        codeSegment.append("\tmov eax, 0\n");          // condition fausse
        codeSegment.append("\tjmp ").append(labelFin).append("\n");
        codeSegment.append(labelVrai).append(":\n");
        codeSegment.append("\tmov eax, 1\n");          // condition vraie
        codeSegment.append(labelFin).append(":\n");
    }

    // ─── Expression arithmétique / booléenne ─────────────────────────────────

    /**
     * Génère le code pour une expression.
     * Invariant de sortie : le résultat est toujours dans eax.
     *
     * Cas traités :
     *   ArbreEntier  → mov eax, valeur
     *   ArbreIdent   → mov eax, variable  (ou "in eax" si INPUT)
     *   ArbreUnaire  → négation (-), NOT
     *   ArbreBinaire → +, -, *, /, % (mod), AND, OR
     *
     * Pour les opérations binaires, on utilise la pile pour préserver
     * l'opérande gauche pendant l'évaluation de l'opérande droite :
     *   1. évaluer gauche → eax
     *   2. push eax          (sauvegarder gauche)
     *   3. évaluer droite → eax
     *   4. pop ebx           (restaurer gauche dans ebx)
     *   5. opération entre eax (droite) et ebx (gauche)
     */
    private void genererExpression(Arbre arbre) {

        if (arbre instanceof ArbreEntier e) {
            codeSegment.append("\tmov eax, ").append(e.valeur).append("\n");

        } else if (arbre instanceof ArbreIdent id) {
            if (id.nom.equalsIgnoreCase("INPUT")) {
                codeSegment.append("\tin eax\n");
            } else {
                codeSegment.append("\tmov eax, ").append(id.nom).append("\n");
            }

        } else if (arbre instanceof ArbreUnaire u) {
            genererExpression(u.expression);
            switch (u.operateur) {
                case "MOINS", "-" -> codeSegment.append("\tneg eax\n");
                case "NOT" -> {
                    codeSegment.append("\ttest eax, eax\n");
                    codeSegment.append("\tsetz al\n");
                    codeSegment.append("\tmovzx eax, al\n");
                }
            }

        } else if (arbre instanceof ArbreBinaire bin) {

            // ── IF/THEN_ELSE : intercepter AVANT le push/pop ─────────────────
            if (bin.operateur.equals("IF")) {
                // bin.gauche = condition, bin.droite = ArbreBinaire("THEN_ELSE", alors, sinon)
                int id = compteurLabel++;
                genererCondition(bin.gauche, id);
                codeSegment.append("\tjz fin_then_").append(id).append("\n");
                if (bin.droite instanceof ArbreBinaire tb && tb.operateur.equals("THEN_ELSE")) {
                    genererExpression(tb.gauche);                                      // branche THEN → eax
                    codeSegment.append("\tjmp fin_if_").append(id).append("\n");
                    codeSegment.append("fin_then_").append(id).append(":\n");
                    genererExpression(tb.droite);                                      // branche ELSE → eax
                    codeSegment.append("fin_if_").append(id).append(":\n");
                } else {
                    genererExpression(bin.droite);
                    codeSegment.append("fin_then_").append(id).append(":\n");
                }
                return; // ne pas tomber dans le push/pop ci-dessous
            }

            // ── Opérations binaires arithmétiques/logiques ───────────────────
            // Étape 1 : évaluer GAUCHE → eax
            genererExpression(bin.gauche);
            // Étape 2 : sauvegarder sur la pile
            codeSegment.append("\tpush eax\n");
            // Étape 3 : évaluer DROITE → eax
            genererExpression(bin.droite);
            // Étape 4 : restaurer gauche dans ebx
            codeSegment.append("\tpop ebx\n");
            // ebx = gauche, eax = droite

            switch (bin.operateur) {
                case "+" -> codeSegment.append("\tadd eax, ebx\n");
                case "-" -> {
                    codeSegment.append("\tsub ebx, eax\n");
                    codeSegment.append("\tmov eax, ebx\n");
                }
                case "*" -> codeSegment.append("\tmul eax, ebx\n");
                case "/" -> {
                    codeSegment.append("\tdiv ebx, eax\n");
                    codeSegment.append("\tmov eax, ebx\n");
                }
                case "%", "MOD", "mod" -> {
                    codeSegment.append("\tmov ecx, eax\n");
                    codeSegment.append("\tmov eax, ebx\n");
                    codeSegment.append("\tdiv ebx, ecx\n");
                    codeSegment.append("\tmul ebx, ecx\n");
                    codeSegment.append("\tsub eax, ebx\n");
                }
                case "AND" -> {
                    codeSegment.append("\tand eax, ebx\n");
                    codeSegment.append("\ttest eax, eax\n");
                    codeSegment.append("\tsetne al\n");
                    codeSegment.append("\tmovzx eax, al\n");
                }
                case "OR" -> {
                    codeSegment.append("\tor eax, ebx\n");
                    codeSegment.append("\ttest eax, eax\n");
                    codeSegment.append("\tsetne al\n");
                    codeSegment.append("\tmovzx eax, al\n");
                }
            }

        } else if (arbre instanceof ArbreAppel appel) {
            // Passer les arguments sur la pile en ordre inverse
            List<Arbre> args = appel.arguments;
            for (int i = args.size() - 1; i >= 0; i--) {
                genererExpression(args.get(i));
                codeSegment.append("\tpush eax\n");
            }
            codeSegment.append("\tcall ").append(appel.nomFonction).append("\n");
            codeSegment.append("\tadd esp, ").append(appel.arguments.size() * 4).append("\n");
            // résultat dans eax

        } else if (arbre instanceof ArbreLambda lambda) {
            // Générer un label unique pour cette fonction
            String nomLabel = "lambda_" + compteurLabel++;
            // Sauter par-dessus le corps
            codeSegment.append("\tjmp fin_").append(nomLabel).append("\n");
            codeSegment.append(nomLabel).append(":\n");
            // Prologue
            codeSegment.append("\tpush ebp\n");
            codeSegment.append("\tmov ebp, esp\n");
            // Charger les paramètres depuis la pile vers les variables
            for (int i = 0; i < lambda.parametres.size(); i++) {
                String param = lambda.parametres.get(i);
                if (!variablesDéclarées.contains(param)) {
                    dataSegment.append("\t").append(param).append(" DD\n");
                    variablesDéclarées.add(param);
                }
                int offset = 8 + i * 4;
                codeSegment.append("\tmov eax, [ebp+").append(offset).append("]\n");
                codeSegment.append("\tmov ").append(param).append(", eax\n");
            }
            // Corps → résultat dans eax
            genererExpression(lambda.corps);
            // Épilogue
            codeSegment.append("\tpop ebp\n");
            codeSegment.append("\tret\n");
            codeSegment.append("fin_").append(nomLabel).append(":\n");
        }
    }
}