# 🔐 Gestionnaire de Mots de Passe Sécurisé
### Système Client-Serveur avec RPC

> Projet académique — École d'Ingénierie Digitale et d'Intelligence Artificielle  
> Spécialité : Cyber Sécurité | Année Universitaire 2023-2024

**Auteurs :** Khadija BOUARGALNE • Imane BENELFAKIR • Ahmed ELFILALI

---

## 📌 Description

Application de gestion de mots de passe sécurisée implémentant une architecture **client-serveur avec communication RPC (Remote Procedure Call)**. Le système offre un stockage centralisé et chiffré des identifiants avec une interface graphique Java Swing, une authentification forte et une journalisation complète des activités.

---

## 🚀 Fonctionnalités

### 👤 Authentification
- Inscription et connexion sécurisées
- Vérification des credentials côté serveur
- Création de session avec token JWT
- Hachage des mots de passe en SHA-256

### 🗂️ Gestion des mots de passe (CRUD complet)
- ➕ **Ajout** — Enregistrement d'un service, nom d'utilisateur et mot de passe chiffré
- 👁️ **Visualisation sécurisée** — Affichage temporaire avec timer automatique
- ✏️ **Modification** — Formulaire pré-rempli avec double confirmation par mot de passe maître
- 🗑️ **Suppression** — Double confirmation avant suppression définitive

### 🖥️ Interface d'administration serveur
- Monitoring en temps réel (clients connectés, adresses IP, durée des sessions)
- Gestion des sessions (connexion/déconnexion)
- Statistiques complètes du serveur
- Journal des événements (Logs du Serveur)

---

## 🛠️ Stack Technologique

| Catégorie | Technologie | Description |
|---|---|---|
| Langage | Java 11 | Code principal |
| Interface | Java Swing | GUI client |
| Communication | RPC over SSL/TLS | Appels distants sécurisés |
| Chiffrement | AES-128 | Protection des données |
| Hachage | SHA-256 | Stockage des mots de passe |
| Base de données | SQLite | Persistance des données |
| Authentification | JWT | Gestion des sessions |

---

## 🏗️ Architecture

```
projet-rpc/
├── projetrpc/
│   └── src/
│       ├── client/
│       │   └── PasswordManagerClient.java   # Interface graphique Swing
│       └── server/
│           ├── PasswordManagerServer.java   # Serveur RPC (port 8080)
│           ├── RPCRequest.java              # Modèle de requête
│           ├── RPCResponse.java             # Modèle de réponse
│           └── ClientInfo.java             # Gestion des sessions
├── projetRPC.pdf                            # Rapport du projet
└── README.md
```

---

## 🔒 Sécurité

L'application implémente une architecture de sécurité **multi-couches** :

```
Client
  │
  ├── HTTPS / TLS 1.2 ──────────► Serveur
  │                                   │
  └── SHA-256 Auth                    ├── AES-128 + UserKey ──► BDD SQLite
                                      └── Logs
```

### Journalisation des événements

| Événement | Niveau | Information enregistrée |
|---|---|---|
| Connexion | INFO | Adresse IP + utilisateur |
| Ajout | DEBUG | Service + timestamp |
| Modification | WARN | Ancienne/nouvelle valeur |
| Suppression | SEVERE | ID + confirmation |

---

## ⚙️ Prérequis

- **Java JDK 11+**
- **Maven** (ou IntelliJ IDEA / Eclipse)
- **SQLite** (inclus via dépendance)

---

## 🔧 Installation et lancement

### 1. Cloner le projet

```bash
git clone https://github.com/khadija123brg/projet-rpc.git
cd projet-rpc/projetrpc
```

### 2. Compiler le projet

```bash
mvn clean install
```

### 3. Lancer le serveur (port 8080)

```bash
mvn exec:java -Dexec.mainClass="server.PasswordManagerServer"
```

### 4. Lancer le client (dans un autre terminal)

```bash
mvn exec:java -Dexec.mainClass="client.PasswordManagerClient"
```

---

## 📄 Documentation

Le rapport complet est disponible dans **[projetRPC.pdf](./projetRPC.pdf)** et couvre :
- L'architecture technique détaillée
- Les mécanismes de sécurité implémentés
- Les fonctionnalités avec captures d'écran
- L'analyse des composants serveur

---

## 👩‍💻 Auteurs

| Nom | GitHub |
|---|---|
| Khadija BOUARGALNE | [khadija123brg](https://github.com/khadija123brg) |
| Imane BENELFAKIR | — |
| Ahmed ELFILALI | — |
