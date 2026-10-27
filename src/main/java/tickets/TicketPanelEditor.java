package tickets;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import database.Database;
import net.dv8tion.jda.api.entities.Role;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.components.separator.Separator;

import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;

import net.dv8tion.jda.api.modals.Modal;



public class TicketPanelEditor extends ListenerAdapter {

    public void openRoleSelector(
            SlashCommandInteractionEvent event,
            TicketPanelData panelData,
            boolean staffRoles
    ) {
        List<String> selectedRoleIds = staffRoles ? panelData.staffRoles : panelData.pingRoles;
        List<EntitySelectMenu.DefaultValue> defaults = selectedRoleIds.stream()
                .map(event.getGuild()::getRoleById)
                .filter(java.util.Objects::nonNull)
                .map(EntitySelectMenu.DefaultValue::from)
                .toList();
        String roleType = staffRoles ? "staff" : "ping";
        EntitySelectMenu.Builder selector = EntitySelectMenu.create(
                        "panel_roles:" + roleType + ":" + panelData.id,
                        EntitySelectMenu.SelectTarget.ROLE
                )
                .setPlaceholder(staffRoles ? "Select staff roles" : "Select ping roles")
                .setRequiredRange(0, 25);
        if (!defaults.isEmpty()) {
            selector.setDefaultValues(defaults);
        }
        event.reply("Choose up to 25 roles. Your selection replaces the panel's current "
                        + (staffRoles ? "staff-role" : "ping-role") + " list.")
                .addComponents(ActionRow.of(selector.build()))
                .setEphemeral(true)
                .queue();
    }

    public void open(SlashCommandInteractionEvent event, TicketPanelData panelData) {
        TextDisplay title = TextDisplay.of("# TicketPanelEditor");
        TextDisplay subTitle = TextDisplay.of("## Editing: " + panelData.name);
        TextDisplay description = TextDisplay.of("**Configure how this ticket panel works.**");

        TextDisplay categoryTotal = TextDisplay.of("**Categories: " + panelData.categories.size() + "**");
        TextDisplay staffRolesTotal = TextDisplay.of("**Staff Roles: " + panelData.staffRoles.size() + "**");
        TextDisplay pingRolesTotal = TextDisplay.of("**Ping Roles: " + panelData.pingRoles.size() + "**");
        TextDisplay ticketCategory = TextDisplay.of(panelData.ticketCategoryId == null
            ? "**Ticket Category: Not set**"
            : "**Ticket Category: <#" + panelData.ticketCategoryId + ">" + "**");

        Button button1 = Button.primary("panel_edit:content:" + panelData.id, "Content");
        Button button2 = Button.primary("panel_edit:categories:" + panelData.id, "Categories");
        Button button3 = Button.primary("panel_edit:staff_roles:" + panelData.id, "Staff Roles");
        Button button4 = Button.primary("panel_edit:ping_roles:" + panelData.id, "Ping Roles");
        Button button5 = Button.primary("panel_edit:ticket_category:" + panelData.id, "Ticket Category");
        Button button6 = Button.primary("panel_edit:category_questions:" + panelData.id, "Category Questions");
        Container container = Container.of(
            title,
            subTitle,
            description,
            Separator.createDivider(Separator.Spacing.LARGE),
            categoryTotal,
            staffRolesTotal,
            pingRolesTotal,
            ticketCategory,
            Separator.createDivider(Separator.Spacing.LARGE),
            TextDisplay.of("## Edit:"),
            ActionRow.of(button1, button2, button3),
            ActionRow.of(button4, button5, button6),
            Separator.createDivider(Separator.Spacing.LARGE),
            TextDisplay.of("-# © 2026 Garnet • All rights reserved.")
        );

        MessageCreateBuilder message = new MessageCreateBuilder()
            .useComponentsV2()
            .addComponents(container);

        event.reply(message.build()).setEphemeral(true).queue();
    }


    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getButton().getCustomId().startsWith("panel_edit:")) {
            return;
        }

        String[] parts = event.getButton().getCustomId().split(":");

        if (parts.length != 3) {
            return;
        }

        String action = parts[1];
        int panelId;
        try {
            panelId = Integer.parseInt(parts[2]);
        } catch (NumberFormatException exception) {
            event.reply("Invalid ticket panel ID.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (!Set.of("content", "categories", "staff_roles", "ping_roles", "ticket_category",
                "category_questions").contains(action)) {
            event.reply("That ticket panel setting isn't available.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (event.getGuild() == null) {
            event.reply("This editor can only be used inside a server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (event.getMember() == null || !TicketPermissions.canManagePanels(event.getMember())) {
            event.reply("You don't have permission to edit ticket panels.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            TicketPanelData panelData = Database.getPanelDataById(event.getGuild().getId(), panelId);
            if (panelData == null) {
                event.reply("That ticket panel no longer exists.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            if ("category_questions".equals(action)) {
                TextInput categoryInput = TextInput.create("category", TextInputStyle.SHORT)
                        .setRequired(true)
                        .setPlaceholder("Exact category name from the Categories setting")
                        .build();
                TextInput questionsInput = TextInput.create("questions", TextInputStyle.PARAGRAPH)
                        .setRequired(false)
                        .setPlaceholder("One prompt per line (up to 5; leave blank to remove)")
                        .build();
                Modal modal = Modal.create("panel_edit:category_questions:" + panelId, "Category Questions")
                        .addComponents(Label.of("Ticket Category", categoryInput),
                                Label.of("Questions", questionsInput))
                        .build();
                event.replyModal(modal).queue();
                return;
            }

            TextInput.Builder contentBuilder = TextInput.create("value", TextInputStyle.PARAGRAPH)
                    .setRequired(false);
            String currentValue = switch (action) {
                case "content" -> panelData.content;
                case "categories" -> String.join(", ", panelData.categories);
                case "staff_roles" -> formatMentions(panelData.staffRoles, "role");
                case "ping_roles" -> formatMentions(panelData.pingRoles, "role");
                case "ticket_category" -> panelData.ticketCategoryId == null
                        ? ""
                        : "<#" + panelData.ticketCategoryId + ">";
                default -> "";
            };
            if (currentValue != null && !currentValue.isBlank()) {
                contentBuilder.setValue(currentValue);
            }
            TextInput content = contentBuilder.build();
            String label = switch (action) {
                case "content" -> "Panel Content";
                case "categories" -> "Categories (comma-separated)";
                case "staff_roles" -> "Staff Roles (mentions or IDs)";
                case "ping_roles" -> "Ping Roles (mentions or IDs)";
                case "ticket_category" -> "Ticket Channel Category (mention or ID)";
                default -> "Panel Setting";
            };
            Label contentLabel = Label.of(label, content);

            Modal modal = Modal.create("panel_edit:" + action + ":" + panelId, "Edit " + label)
                    .addComponents(contentLabel)
                    .build();
            event.replyModal(modal).queue();
        } catch (SQLException exception) {
            exception.printStackTrace();
            event.reply("Couldn't load the panel content. Please try again.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        if (parts.length != 3 || !"panel_roles".equals(parts[0])
                || !Set.of("staff", "ping").contains(parts[1])) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null
                || !TicketPermissions.canManagePanels(event.getMember())) {
            event.reply("You don't have permission to edit ticket panels.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            int panelId = Integer.parseInt(parts[2]);
            TicketPanelData panelData = Database.getPanelDataById(event.getGuild().getId(), panelId);
            if (panelData == null) {
                event.reply("That ticket panel no longer exists.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            List<String> roleIds = event.getValues().stream()
                    .filter(Role.class::isInstance)
                    .map(Role.class::cast)
                    .filter(role -> !role.isPublicRole())
                    .map(Role::getId)
                    .distinct()
                    .toList();
            if (roleIds.size() > 25) {
                event.reply("Select no more than 25 roles.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            if ("staff".equals(parts[1])) {
                panelData.staffRoles = new ArrayList<>(roleIds);
            } else {
                panelData.pingRoles = new ArrayList<>(roleIds);
            }
            Database.savePanel(panelData);
            event.reply("Saved " + roleIds.size() + " "
                            + ("staff".equals(parts[1]) ? "staff" : "ping")
                            + " role(s) for **" + panelData.name + "**.")
                    .setEphemeral(true)
                    .queue();
        } catch (NumberFormatException exception) {
            event.reply("Invalid ticket panel ID.")
                    .setEphemeral(true)
                    .queue();
        } catch (SQLException exception) {
            exception.printStackTrace();
            event.reply("Couldn't save the selected roles. Please try again.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String customId = event.getModalId();
        if (!customId.startsWith("panel_edit:")) {
            return;
        }

        if (event.getGuild() == null) {
            event.reply("This editor can only be used inside a server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (event.getMember() == null || !TicketPermissions.canManagePanels(event.getMember())) {
            event.reply("You don't have permission to edit ticket panels.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            String[] parts = customId.split(":");
            if (parts.length != 3) {
                event.reply("Invalid ticket panel setting.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            String action = parts[1];
            int panelId = Integer.parseInt(parts[2]);
            TicketPanelData panelData = Database.getPanelDataById(event.getGuild().getId(), panelId);
            if (panelData == null) {
                event.reply("That ticket panel no longer exists.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            if ("category_questions".equals(parts[1])) {
                String categoryName = event.getValue("category").getAsString().trim();
                if (!panelData.categories.contains(categoryName)) {
                    event.reply("Add that exact category to the panel before configuring its questions.")
                            .setEphemeral(true)
                            .queue();
                    return;
                }
                String promptText = event.getValue("questions") == null
                        ? ""
                        : event.getValue("questions").getAsString();
                List<String> questions = parseQuestions(promptText);
                Database.savePanelQuestions(panelId, categoryName, questions);
                event.reply(questions.isEmpty()
                                ? "Questions removed for **" + categoryName + "**."
                                : "Saved " + questions.size() + " question(s) for **" + categoryName + "**.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            var valueInput = event.getValue("value");
            String value = valueInput == null ? "" : valueInput.getAsString().trim();
            switch (action) {
                case "content" -> panelData.content = value;
                case "categories" -> panelData.categories = parseCategories(value);
                case "staff_roles" -> panelData.staffRoles = parseRoles(value, event.getGuild());
                case "ping_roles" -> panelData.pingRoles = parseRoles(value, event.getGuild());
                case "ticket_category" -> panelData.ticketCategoryId = parseTicketCategory(value, event);
                default -> {
                    event.reply("That ticket panel setting isn't available.")
                            .setEphemeral(true)
                            .queue();
                    return;
                }
            }
            Database.savePanel(panelData);
            event.reply("Ticket panel setting saved.")
                    .setEphemeral(true)
                    .queue();
        } catch (NumberFormatException exception) {
            event.reply("Invalid ticket panel ID.")
                    .setEphemeral(true)
                    .queue();
        } catch (IllegalArgumentException exception) {
            event.reply(exception.getMessage())
                    .setEphemeral(true)
                    .queue();
        } catch (SQLException exception) {
            exception.printStackTrace();
            event.reply("Couldn't save the panel content. Please try again.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private List<String> parseCategories(String value) {
        if (value.isBlank()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String category = item.trim();
            if (!category.isEmpty()) {
                if (category.length() > 100) {
                    throw new IllegalArgumentException("Each category must be 100 characters or fewer.");
                }
                categories.add(category);
            }
        }
        if (categories.size() > 25) {
            throw new IllegalArgumentException("A ticket panel can have at most 25 categories.");
        }
        return new ArrayList<>(categories);
    }

    private List<String> parseQuestions(String value) {
        if (value.isBlank()) {
            return new ArrayList<>();
        }
        List<String> questions = value.lines()
                .map(String::trim)
                .filter(question -> !question.isEmpty())
                .toList();
        if (questions.size() > 5) {
            throw new IllegalArgumentException("A category can have at most five questions.");
        }
        if (questions.stream().anyMatch(question -> question.length() > 45)) {
            throw new IllegalArgumentException("Each question must be 45 characters or fewer.");
        }
        return questions;
    }

    private List<String> parseRoles(String value, net.dv8tion.jda.api.entities.Guild guild) {
        if (value.isBlank()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String roleId = item.trim().replaceAll("^<@&|>$", "");
            if (!roleId.matches("\\d{17,20}")
                    || guild.getRoleById(roleId) == null
                    || guild.getPublicRole().getId().equals(roleId)) {
                throw new IllegalArgumentException("Use valid role mentions or IDs from this server.");
            }
            roles.add(roleId);
        }
        if (roles.size() > 25) {
            throw new IllegalArgumentException("A ticket panel can have at most 25 roles in each list.");
        }
        return new ArrayList<>(roles);
    }

    private String parseTicketCategory(String value, ModalInteractionEvent event) {
        if (value.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("^(?:<#)?(\\d{17,20})>?$").matcher(value);
        if (!matcher.matches() || event.getGuild().getCategoryById(matcher.group(1)) == null) {
            throw new IllegalArgumentException("Use a valid category mention or category ID from this server.");
        }
        return matcher.group(1);
    }

    private String formatMentions(List<String> values, String type) {
        String prefix = "role".equals(type) ? "<@&" : "<#";
        return values.stream().map(id -> prefix + id + ">").reduce((left, right) -> left + ", " + right).orElse("");
    }
}