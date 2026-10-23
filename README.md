# Garnet 💎

**Garnet** is a Java-based Discord bot focused on giving communities practical tools for server management, configuration, utilities, and support — while keeping the experience clean, modern, and easy to use.

> 🚧 **Garnet is currently under active development.**

## ✨ Features

- 🤖 Discord bot functionality powered by JDA
- 📋 Customisable embeds
- 📜 Configurable server rules
- 👋 Welcome messages
- 🗄️ SQLite database persistence
- 🎫 Ticket system
- 🛠️ Server-management and utility commands

## 🧰 Technology Stack

- **Java 25**
- **JDA 6.7.0**
- **Maven**
- **SQLite**

## 🎫 Ticket System

Garnet's ticket system is being developed from the ground up with a focus on reliable data management and maintainable code.

### Progress

- [x] Ticket data model
- [x] Create tickets
- [x] Close tickets
- [x] Reopen tickets
- [x] Claim tickets
- [x] Unclaim tickets
- [x] Transfer tickets
- [x] SQLite persistence
- [x] Delete ticket data
- [x] Ticket commands for closing, reopening, claiming, unclaiming, transferring, deleting, and transcribing tickets
- [x] Configurable ticket panels with category selection
- [x] Text transcripts (latest 1,000 messages)
- [x] Server and panel ticket configuration, including channel categories and staff/ping roles

Use `/ticket panel create`, `/ticket panel edit`, and `/ticket panel send` to publish a panel. In the panel editor, configure comma-separated categories, then use **Category Questions** to give each category up to five required form prompts (one prompt per line). Selecting a category opens that form before creating the ticket. New tickets start with an embed containing the submitted answers and controls; only members with **Manage Channels** can claim, unclaim, transfer, delete, or create a transcript. Ticket creators can close and reopen their ticket.

## 📁 Project Structure

Garnet is organised into separate packages for commands, events, database functionality, and ticket management.

The project is developed incrementally, with an emphasis on keeping the code clean, maintainable, and easy to understand.

## 🔐 Policies

- [Privacy Policy](https://github.com/EnderKnight7op/Ender-Labs/blob/main/PRIVACY.md)
- [Terms of Service](https://github.com/EnderKnight7op/Ender-Labs/blob/main/TERMS.md)
- [Security Policy](https://github.com/EnderKnight7op/Ender-Labs/blob/main/SECURITY.md)

## 📌 Status

Garnet is a personal development project and is **not currently intended for general public use**.

Features, documentation, and policies may change as development continues.

## 🔗 Links

- [Discord Server](https://dsc.gg/garnetbot)
- [Source Code](https://github.com/EnderKnight7op/Garnet)
