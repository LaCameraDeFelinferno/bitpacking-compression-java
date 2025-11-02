# Projet de Compression BitPacking Java

Ce projet est une bibliothèque Java performante pour la compression de tableaux d'entiers (`int[]`), conçue pour optimiser l'espace de stockage tout en conservant un accès direct (aléatoire) aux données.

## 🎯 Stratégies de Compression

La bibliothèque implémente trois stratégies de compression distinctes :

### 🔄 Crossing (Chevauchement)
La compression la plus dense. Les entiers sont tassés dans un flux de bits continu, quitte à ce qu'un entier soit "à cheval" sur deux mots de 32 bits.

### 📦 NoCrossing (Sans Chevauchement)
Une compression plus simple qui garantit que chaque entier tient entièrement dans un mot de 32 bits. Cela peut gaspiller de l'espace (padding) mais simplifie les calculs d'accès.

### ⚡ Overflow (Débordement)
Une stratégie adaptative idéale pour les ensembles de données contenant une majorité de petites valeurs et quelques "outliers" (valeurs très grandes), qu'elle stocke dans une zone de débordement séparée.

## 🏗️ Architecture

L'architecture est repose sur l'abstraction (`BitIO` pour la manipulation de bits), des en-têtes de métadonnées (`Headers`) et un patron de conception "Factory" (`CompressorFactory`).

## 📁 Structure du Projet

Tous les fichiers source se trouvent dans le package `io`.

### 📚 Classes de la Bibliothèque
- `IntCompressor.java`
- `BitPackingBase.java` 
- `BitPackingCrossing.java`
- `BitPackingNoCrossing.java`
- `BitPackingOverflow.java`
- `BitIO.java`
- `Headers.java`
- `CompressorFactory.java`
- `CompressionType.java`

### 🔌 Adaptateur (Legacy)
- `BitPackingFactory.java` (utilisé par l'ancien Main, non fourni ici)

### 🚀 Exécutables
- **`Main.java`** : Le banc d'essai interactif
- **`AutomatedBenchmark.java`** : Le banc d'essai automatisé pour comparer les 3 stratégies
- **`SmokeTest.java`** : Un test de validation rapide

## 🔨 Compilation

Pour compiler le projet, placez tous les fichiers `.java` dans un dossier nommé `io`. La version JDK 22.0.2 a été utilisée pour réaliser et est conseillée pour compiler le projet.Ensuite, ouvrez un terminal dans le répertoire parent du dossier `io` (pas à l'intérieur) et exécutez la commande `javac` :

```bash
# Assurez-vous d'être DANS LE DOSSIER PARENT de 'io'
javac io/*.java
```

Cela compilera tous les fichiers `.java` et créera les fichiers `.class` correspondants dans le dossier `io`.

## 🏃‍♂️ Exécution

Ce projet contient trois points d'entrée (méthodes `main`) que vous pouvez exécuter. Assurez-vous de les lancer depuis le même répertoire parent (celui où vous avez lancé `javac`).

### 1. 🎮 Benchmark Interactif (`io.Main`)

C'est le programme principal qui vous guide à travers une série de questions pour configurer un banc d'essai personnalisé pour une seule stratégie de compression.

**Comment le lancer :**
```bash
java io.Main
```

**À quoi s'attendre :**
Le programme vous posera des questions interactives :
- Quel mode de compression (Overlap, NoOverlap, Overflow) ?
- Quel type de données (Uniforme ou Outliers) ?
- Quels paramètres de données (taille du tableau, valeur max, etc.) ?
- Quels paramètres de benchmark (warmups, runs) ?

Il exécutera ensuite le test et affichera un rapport détaillé pour la configuration que vous avez choisie.

```
╔═══════════════════════════════════════════════════════╗
║          BIT PACKING - COMPRESSION BENCHMARK          ║
╚═══════════════════════════════════════════════════════╝

┌─ ÉTAPE 1/4 : MODE DE COMPRESSION ────────────────────┐
│                                                      │
│  [1] Overlap      - Chevauchement                    │
│  [2] NoOverlap    - Sans chevauchement               │
│  [3] Overflow     - Gestion débordement              │
│                                                      │
└──────────────────────────────────────────────────────┘
➤ Votre choix [1-3]:
```

### 2. 🤖 Benchmark Automatisé (`io.AutomatedBenchmark`)

C'est le programme le plus utile pour le rapport. Il n'est pas interactif. Il exécute une série de scénarios de test prédéfinis (données uniformes, données avec outliers, etc.) et compare les performances (vitesse et taille) des trois stratégies côte à côte.

**Comment le lancer :**
```bash
java io.AutomatedBenchmark
```

**À quoi s'attendre :**
Le programme s'exécutera pendant quelques secondes et affichera une série de tableaux comparatifs, vous permettant de voir quelle stratégie est la meilleure pour quel type de données.

```
==============================================
         AUTOMATED COMPRESSION BENCHMARK
==============================================

-- Scénario: Uniformes (k=9, 32%9!=0) --
Stratégie    | Comp         | Decomp       | Get (ns/op)    | Taille
--------------------------------------------------------------------------------
Overlap      | 15.123 ms    | 12.456 ms    |          12.34 | 1 125 020 B
NoOverlap    | 10.123 ms    | 8.456 ms     |           9.87 | 1 375 020 B
Overflow     | 18.123 ms    | 15.456 ms    |          14.56 | 1 250 020 B

-- Scénario: Outliers (1%, k~6/20) --
Stratégie    | Comp         | Decomp       | Get (ns/op)    | Taille
--------------------------------------------------------------------------------
Overlap      | 30.123 ms    | 25.456 ms    |          15.67 | 2 500 020 B
NoOverlap    | 28.123 ms    | 22.456 ms    |          12.34 | 2 750 020 B
Overflow     | 20.123 ms    | 18.456 ms    |          16.78 | 1 190 020 B
```

### 3. 🧪 Test de Validation (`io.SmokeTest`)

C'est un test de "validation" (ou "smoke test") très simple. Il ne mesure pas les performances. Il crée un petit tableau de données, le compresse et le décompresse avec les 3 stratégies pour vérifier que le résultat est correct (`Arrays.equals`).

C'est utile pour vérifier rapidement que la compilation s'est bien passée et que les algorithmes fonctionnent.

**Comment le lancer :**
```bash
java io.SmokeTest
```

**À quoi s'attendre :**
Un retour rapide confirmant que les tests sont passés.

```
Testing CROSSING
  decompress OK: true, compressed ints=3126
  sample get() OK: true
Testing NO_CROSSING
  decompress OK: true, compressed ints=3126
  sample get() OK: true
Testing OVERFLOW
  decompress OK: true, compressed ints=3126
  sample get() OK: true
Smoke test done.
```
Projet Universitaire de M. BENADY Semy pour l'Université Côte d'Azur, UE Génie Logiciel et Projet DEV de M. J.C. Régin. 