# Note pour une nouvelle release Waarp-Vitam

## 1  Pré-requis

Les outils suivants sont nécessaires. Des variantes peuvent être utilisées :

- Java 1.11 (même si la version est compilée pour être compatible avec Java 8)
- Maven 3.6.3
- Artifactory (ou équivalent) pour pré-publier les jar (via Docker sur le PC :
  https://www.jfrog.com/confluence/display/JFROG/Installing+Artifactory )
  - Note : Afin d'autoriser Maven à communiquer avec SonarQube, dans votre
    fichier `~/.m2/settings.xml`, il faut ajouter les éléments suivants :
    
    `<servers>
      <server>
        <id>centralArtifactory</id>
        <username>USER_NAME</username>
        <password>PASSWORD</password>
      </server>
    </servers>`

- SonarQube (via Docker par exemple :
  https://docs.sonarqube.org/latest/setup/get-started-2-minutes/ )
  - Note : Afin d'autoriser Maven à communiquer avec SonarQube, dans votre
    fichier `~/.m2/settings.xml`, il faut ajouter les éléments suivants :
    
    `<properties>
       <sonar.host.url>http://localhost:9000</sonar.host.url>
       <sonar.login>USER_CODE</sonar.login>
     </properties>`

    
- De préférence un IDE moderne comme IntelliJ (en version communautaire) :
  un modèle de configuration est disponible.
- Un Host Linux (Debian, Ubuntu ou Redhat ou assimilé) avec au moins 2 cœurs et
  au moins 8 Go de mémoire disponibles ainsi que 20 Go de disques (pour les
  sources et compilation) et 10 Go sur le répertoire `/tmp`


## 2  Vérification

Il s’agit de s’assurer du bon fonctionnement de la version selon les tests
automatisés et de la qualité du code.

### 2.1  Via Maven

#### 2.1.1  Mise à jour des versions dans le `pom.xml`

Les compilations se font avec une JDK 11 mais produisent des JAR compatibles avec
Java 8 afin de rester compatible avec toutes les versions de Vitam.

Le code s'appuie sur la version JRE 8 de Waarp-All.

Le champ à modifier est `waarp.version` avec une valeur du type `x.y.z`.

#### 2.1.2 Validation Maven

A la racine du projet `Waarp-Vitam` :

- Vérification des dépendances : ATTENTION, `Waarp-Vitam` est prévu pour des
  dépendances pures Java 8, certains modules ne peuvent donc être upgradés.
  - Dépendances logicielles Java : l’inspection des résultats proposera des
    mises à jour définies dans le `pom.xml` (à prendre avec précaution)
    - `mvn versions:display-dependency-updates`
  - Dépendances logicielles Maven
    - `mvn versions:display-plugin-updates`
- Vérification du code
  - `mvn clean install`
    - Ceci vérifiera l’ensemble des modules selon les tests Junits et les tests
      IT en mode « simplifiés » (raccourcis).

Ces tests produisent différents fichiers, dont ceux relatifs à SonarQube
`jacoco.xml`.

### 2.2  Autres tests

Il y a d’autres tests possibles qui nécessitent la mise en place d’un
environnement ad-hoc, basé sur Docker ou une VM VMware, comme celle fournie par
le Programme Vitam.

Se reporter au répertoire `WaarpVitamInstall` et en particulier le fichier
`README.md`.

### 2.3  Étape SonarQube

Cette étape permet de générer l’analyse complète SonarQube qui sera intégrée
partiellement dans le Site Maven.

Prérequis : les tests complets (`mvn clean install`) doivent avoir été
préalablement exécutés avec succès.

SonarQube doit être actif durant cette étape.

- `mvn sonar:sonar`
- Il est possible et recommandé ensuite de constater les résultats sur
  l’interface Web de SonarQube.
- Si nécessaire, apportez encore des corrections pour des failles de sécurité
  ou mauvaises pratiques, le cas échéant en rejouant tous les tests depuis le
  début.

SonarQube peut être arrêté une fois cette étape terminée.


## 3  Publication

Il s’agit ici de préparer les éléments nécessaires pour la publication JAR,
HTML et GITHUB.

### 3.1  Publication des JAR

Grâce à Artifactory (ou équivalent), qui doit être actif durant cette étape,
via Maven, il est possible de pré-publier les Jar dans un dépôt local maven :

- `mvn clean deploy -DskipTests`

Note pour Artifactory : l’export sera effectué sur le répertoire attaché au
host non Docker dans `/jfrog/artifactory/logs/maven2/`.

Il est possible de modifier l'adresse du service Maven de publication dans le
profile `release`.

Une fois publiés dans le dépôt Maven local, il faut suivre la procédure pour
recopier le résultat dans le dépôt GITHUB correspondant. Pour Artifactory :

- Une fois connecté comme administrateur
- Déclencher la réindexation du dépôt `libs-release-local` (menu système)
- Déclencher l’export du dépôt `libs-release-local` en ayant pris soin de
  cocher les cases « create .m2 compatible export » et « Exclude Metadata »
  en sélectionnant le répertoire de sortie `/opt/jfrog/logs/maven2` dans
  l’image Docker, qui sera situé in fine dans `/jfrog/logs/maven2`.
- Dans l’interface de consultation du dépôt `libs-release-local` d’Artifactory,
  cliquer sur le bouton de droite pour pouvoir accéder en mode Web natif à ce
  dépôt et enregistrer sous les deux fichiers placés sous le répertoire
  « .index » dans le répertoire correspondant sous 
  `/jfrog/logs/maven2/libs-release-local/.index`
- En ligne de commande, accordez les droits à tous les fichiers pour être copiés :
  - `sudo chmod -R a+rwX /jfrog/logs/maven2/libs-release-local/`
- Recopier ensuite le contenu dans le répertoire cible (projet `Waarp-Maven2`,
  répertoire `maven2`)
- Vous pouvez ensuite publier la mise à jour sous Github
  - `git add .`
  - `git commit -m « Nom de la release »`
  - `git push origin master`

Artifcatory ou équivalent peut être arrêté à partir d’ici.

NB : la première fois qu’Artifcatory ou équivalent est installé, il faut
installer tous les jars pré-existants dans le dépôt (procédure manuelle ou
automatisée si l’on peut), ceci afin de disposer d’un dépôt complet et
correspondant à l’existant.

### 3.2  HTML

Il s’agit ici de générer les pages automatisées Maven (Site Maven) du projet
`Waarp-Vitam`.
Pour cela, il est conseillé de disposer de 2 clones de `Waarp-Vitam`, l’un pour
s’occuper des sources java et constructions, l’autre pour ne gérer que la
branche `gh-pages`.

#### 3.2.1  Étape Site Maven

Cette étape permet de générer le Site Maven (dans `Waarp-Vitam/target/staging`).

- `mvn site site:stage`

Recopier ensuite le contenu de ce site dans le clone `Waarp-Vitam` pour la
branche `gh-pages` prévu à cet effet et enfin publier :

- `git add .`
- `git commit -m « Nom de la release »`
- `git push origin gh-pages`

### 3.3  GITHUB

Il s’agit ici de publier la release sous Github :

- Préparer une note de version (en langue Anglaise)
- Aller sur le site (la version étant à jour sur la branche maître
  correspondante)
- Créer une nouvelle release sous Github (tags → Releases → Draft a new release)
- Associer au moins le jar complet pour
  la release (trouvés respectivement dans 
  `Waarp-Vitam/target/Waarp-Vitam-1.0.3-jar-with-dependencies.jar`)
- Publier


D’autres étapes sont nécessaires, comme la publication sur le site
officiel de la société Waarp.
