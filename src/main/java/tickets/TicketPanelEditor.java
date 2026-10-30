package tickets;

import database.Database;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.checkbox.Checkbox;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.radiogroup.RadioGroup;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The ticket panel editor: one ephemeral Components V2 message that updates in place.
 *
 * <p>Every component ID has the form {@code pe:<panelId>:<page>:<action>[:<arg>]} so a click can
 * be handled without keeping any state in memory. All changes are saved immediately; the
 * published panel message only changes when the user presses Publish.
 */
public class TicketPanelEditor extends ListenerAdapter {

    private static final String PREFIX = "pe:";
    private static final Pattern HEX_COLOUR = Pattern.compile("^#?[0-9a-fA-F]{6}$");
    private static final Pattern HTTP_URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    public enum Page {
        APPEARANCE("appearance", "Appearance", "Title, colour, images and the button"),
        ROUTING("routing", "Roles & routing", "Staff and ping roles, channels"),
        CATEGORIES("categories", "Categories", "Dropdown categories and their questions"),
        /** Sub-page of Categories that edits the questions of one category; not in the page menu. */
        QUESTIONS("questions", "Questions", "The form questions of one category");

        final String key;
        final String label;
        final String description;

        Page(String key, String label, String description) {
            this.key = key;
            this.label = label;
            this.description = description;
        }

        static Page fromKey(String key) {
            for (Page page : values()) {
                if (page.key.equals(key)) {
                    return page;
                }
            }
            return APPEARANCE;
        }
    }

    private record Ctx(TicketPanelData panel, Page page, String action, String arg) {
    }

    // ---------------------------------------------------------------------------------------
    // Entry points (called from /ticket panel ...)
    // ---------------------------------------------------------------------------------------

    public void open(SlashCommandInteractionEvent event, TicketPanelData panelData) {
        open(event, panelData, Page.APPEARANCE);
    }

    public void open(SlashCommandInteractionEvent event, TicketPanelData panelData, Page page) {
        Container container = render(panelData, page, event.getGuild(), null);
        event.reply(new MessageCreateBuilder().useComponentsV2().addComponents(container).build())
                .setEphemeral(true)
                .queue();
    }

    // ---------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------

    private static String id(TicketPanelData panel, Page page, String action) {
        return PREFIX + panel.id + ":" + page.key + ":" + action;
    }

    private static String id(TicketPanelData panel, Page page, String action, String arg) {
        return id(panel, page, action) + ":" + arg;
    }

    private MessageEditData view(TicketPanelData panel, Page page, Guild guild, String notice) {
        return new MessageEditBuilder().useComponentsV2().setComponents(render(panel, page, guild, notice)).build();
    }

    private MessageEditData questionsView(TicketPanelData panel, int categoryIndex, Guild guild, String notice) {
        return new MessageEditBuilder()
                .useComponentsV2()
                .setComponents(render(panel, Page.QUESTIONS, guild, notice, categoryIndex))
                .build();
    }

    private MessageEditData plainView(String text) {
        return new MessageEditBuilder()
                .useComponentsV2()
                .setComponents(Container.of(TextDisplay.of(text)))
                .build();
    }

    private Container render(TicketPanelData panel, Page page, Guild guild, String notice) {
        return render(panel, page, guild, notice, -1);
    }

    /** {@code categoryIndex} is only used by the QUESTIONS page. */
    private Container render(TicketPanelData panel, Page requested, Guild guild, String notice, int categoryIndex) {
        // The questions page needs a valid category; otherwise fall back to the category list.
        Page page = requested == Page.QUESTIONS && (categoryIndex < 0 || categoryIndex >= panel.categories.size())
                ? Page.CATEGORIES
                : requested;

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# Panel editor: " + truncate(panel.name, 60)));
        children.add(TextDisplay.of(statusLine(panel)));
        if (notice != null) {
            children.add(TextDisplay.of(notice));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));

        switch (page) {
            case APPEARANCE -> addAppearance(children, panel, page);
            case ROUTING -> addRouting(children, panel, page, guild);
            case CATEGORIES -> addCategories(children, panel, page);
            case QUESTIONS -> addQuestions(children, panel, categoryIndex);
        }

        if (page == Page.QUESTIONS) {
            // The question manager has its own Back button instead of the page menu and action row.
            return Container.of(children).withAccentColor(TicketPanel.parseColour(panel.embedColour));
        }

        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(ActionRow.of(pageMenu(panel, page)));
        children.add(ActionRow.of(
                Button.secondary(id(panel, page, "preview"), "Preview"),
                Button.success(id(panel, page, "publish"), panel.messageId == null ? "Publish" : "Update panel"),
                Button.danger(id(panel, page, "delete"), "Delete"),
                Button.secondary(id(panel, page, "done"), "Done")));
        return Container.of(children).withAccentColor(TicketPanel.parseColour(panel.embedColour));
    }

    private String statusLine(TicketPanelData panel) {
        String where = panel.channelId == null
                ? "Not published yet."
                : (panel.messageId == null ? "Channel set: <#" + panel.channelId + ">. Not published yet."
                        : "Published in <#" + panel.channelId + ">.");
        return "-# " + where + " Changes save automatically; press **"
                + (panel.messageId == null ? "Publish" : "Update panel") + "** to show them to members.";
    }

    private StringSelectMenu pageMenu(TicketPanelData panel, Page current) {
        StringSelectMenu.Builder menu = StringSelectMenu.create(id(panel, current, "nav"))
                .setPlaceholder("Go to a page");
        for (Page page : Page.values()) {
            if (page == Page.QUESTIONS) {
                continue;
            }
            menu.addOptions(SelectOption.of(page.label, page.key).withDescription(page.description));
        }
        menu.setDefaultValues(current.key);
        return menu.build();
    }

    private void addAppearance(List<ContainerChildComponent> children, TicketPanelData panel, Page page) {
        String title = orDefault(panel.embedTitle, "*default (panel name)*");
        String description = panel.content == null || panel.content.isBlank()
                ? "*default text*"
                : truncate(panel.content.replace('\n', ' '), 90);
        children.add(row(id(panel, page, "title"),
                "**Title & description**\nTitle: " + truncate(title, 70) + "\nDescription: " + description));

        String colour = panel.embedColour == null ? "#5865F2 *(default)*" : normaliseColour(panel.embedColour);
        children.add(row(id(panel, page, "colour"), "**Colour**\n" + colour));

        children.add(row(id(panel, page, "images"),
                "**Images**\nImage: " + (panel.imageUrl == null ? "*not set*" : "set")
                        + "\nThumbnail: " + (panel.thumbnailUrl == null ? "*not set*" : "set")));

        children.add(row(id(panel, page, "footer"),
                "**Footer**\n" + (panel.footerText == null
                        ? "*default (server name)*"
                        : truncate(panel.footerText, 80))));

        String emoji = panel.buttonEmoji == null ? "" : panel.buttonEmoji + " ";
        String label = orDefault(panel.buttonLabel, TicketPanelData.DEFAULT_BUTTON_LABEL);
        children.add(row(id(panel, page, "button"),
                "**Create button**\n" + emoji + truncate(label, 40) + " · " + styleName(panel.buttonStyle)
                        + (panel.categories.isEmpty() ? "" : "\n-# Not shown while the panel has categories; "
                                + "members use the dropdown instead.")));
    }

    private void addRouting(List<ContainerChildComponent> children, TicketPanelData panel, Page page, Guild guild) {
        children.add(TextDisplay.of("**Staff roles**\n" + mentionList(panel.staffRoles, "@&", guild, true)
                + "\n-# Can claim, close and manage tickets from this panel."));
        children.add(ActionRow.of(roleMenu(panel, page, "staff", "Select staff roles", panel.staffRoles, guild)));

        children.add(TextDisplay.of("**Ping roles**\n" + mentionList(panel.pingRoles, "@&", guild, true)
                + "\n-# Mentioned when a ticket is opened."));
        children.add(ActionRow.of(roleMenu(panel, page, "ping", "Select ping roles", panel.pingRoles, guild)));

        children.add(TextDisplay.of("**Ticket category**\n"
                + (panel.ticketCategoryId == null ? "*server default (/ticket set-category)*"
                        : "<#" + panel.ticketCategoryId + ">")
                + "\n-# Discord category new ticket channels are created in."));
        EntitySelectMenu.Builder category = EntitySelectMenu.create(
                        id(panel, page, "ticketcat"), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setPlaceholder("Select a ticket category")
                .setRequiredRange(0, 1);
        if (panel.ticketCategoryId != null && guild.getCategoryById(panel.ticketCategoryId) != null) {
            category.setDefaultValues(EntitySelectMenu.DefaultValue.channel(panel.ticketCategoryId));
        }
        children.add(ActionRow.of(category.build()));

        children.add(TextDisplay.of("**Panel channel**\n"
                + (panel.channelId == null ? "*not set*" : "<#" + panel.channelId + ">")
                + "\n-# Where the panel is published."));
        EntitySelectMenu.Builder channel = EntitySelectMenu.create(
                        id(panel, page, "channel"), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT)
                .setPlaceholder("Select the panel channel")
                .setRequiredRange(0, 1);
        if (panel.channelId != null && guild.getTextChannelById(panel.channelId) != null) {
            channel.setDefaultValues(EntitySelectMenu.DefaultValue.channel(panel.channelId));
        }
        children.add(ActionRow.of(channel.build()));

        children.add(TextDisplay.of("**Transcript channel**\n"
                + (panel.transcriptChannelId == null
                        ? "*none: transcripts are sent privately to whoever requests them*"
                        : "<#" + panel.transcriptChannelId + ">")
                + "\n-# Where ticket transcripts are posted."));
        EntitySelectMenu.Builder transcript = EntitySelectMenu.create(
                        id(panel, page, "transcript"), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT)
                .setPlaceholder("Select the transcript channel")
                .setRequiredRange(0, 1);
        if (panel.transcriptChannelId != null && guild.getTextChannelById(panel.transcriptChannelId) != null) {
            transcript.setDefaultValues(EntitySelectMenu.DefaultValue.channel(panel.transcriptChannelId));
        }
        children.add(ActionRow.of(transcript.build()));
    }

    private EntitySelectMenu roleMenu(
            TicketPanelData panel, Page page, String kind, String placeholder, List<String> selected, Guild guild) {
        EntitySelectMenu.Builder menu = EntitySelectMenu.create(
                        id(panel, page, kind), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder(placeholder)
                .setRequiredRange(0, 25);
        List<EntitySelectMenu.DefaultValue> defaults = selected.stream()
                .filter(roleId -> guild.getRoleById(roleId) != null)
                .map(EntitySelectMenu.DefaultValue::role)
                .toList();
        if (!defaults.isEmpty()) {
            menu.setDefaultValues(defaults);
        }
        return menu.build();
    }

    private void addCategories(List<ContainerChildComponent> children, TicketPanelData panel, Page page) {
        StringBuilder text = new StringBuilder("**Categories**\n");
        if (panel.categories.isEmpty()) {
            text.append("*none: members see a single create button*");
        } else {
            for (int index = 0; index < panel.categories.size(); index++) {
                String category = panel.categories.get(index);
                int count = questionCount(panel, category);
                text.append(index + 1).append(". ").append(truncate(category, 60))
                        .append(count == 0 ? "" : " · " + count + (count == 1 ? " question" : " questions"))
                        .append('\n');
            }
        }
        text.append("\n-# Members pick a category from a dropdown. Renaming a category removes its questions.");
        children.add(row(id(panel, page, "categories"), text.toString()));

        if (!panel.categories.isEmpty()) {
            StringSelectMenu.Builder menu = StringSelectMenu.create(id(panel, page, "qpick"))
                    .setPlaceholder("Edit the questions for a category");
            for (int index = 0; index < panel.categories.size() && index < 25; index++) {
                menu.addOptions(SelectOption.of(truncate(panel.categories.get(index), 100), Integer.toString(index)));
            }
            children.add(ActionRow.of(menu.build()));
        }
    }

    private int questionCount(TicketPanelData panel, String category) {
        return loadQuestions(panel, category).size();
    }

    private List<TicketQuestion> loadQuestions(TicketPanelData panel, String category) {
        try {
            return Database.getPanelQuestions(panel.id, category);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return List.of();
        }
    }

    /** The question manager for one category: every question has its own Edit button. */
    private void addQuestions(List<ContainerChildComponent> children, TicketPanelData panel, int categoryIndex) {
        String category = panel.categories.get(categoryIndex);
        List<TicketQuestion> questions = loadQuestions(panel, category);
        String categoryArg = Integer.toString(categoryIndex);

        children.add(TextDisplay.of("## Questions: " + truncate(category, 60)
                + "\n-# Members answer these in a form before their ticket is created. Up to "
                + TicketQuestion.MAX_QUESTIONS + " questions."));
        if (questions.isEmpty()) {
            children.add(TextDisplay.of("*No questions: tickets in this category open straight away.*"));
        }
        for (int index = 0; index < questions.size(); index++) {
            TicketQuestion question = questions.get(index);
            String details = question.styleName() + " · " + (question.required() ? "Required" : "Optional")
                    + (question.maxLength() == null ? "" : " · max " + question.maxLength());
            String placeholder = question.placeholder() == null
                    ? ""
                    : "\nPlaceholder: " + truncate(question.placeholder(), 60);
            children.add(Section.of(
                    Button.secondary(id(panel, Page.QUESTIONS, "qedit", categoryArg + ":" + index), "Edit"),
                    TextDisplay.of("**" + (index + 1) + ". " + truncate(question.prompt(), 45) + "**\n"
                            + details + placeholder)));
        }
        if (!questions.isEmpty()) {
            StringSelectMenu.Builder remove = StringSelectMenu.create(id(panel, Page.QUESTIONS, "qremove", categoryArg))
                    .setPlaceholder("Remove a question");
            for (int index = 0; index < questions.size(); index++) {
                remove.addOptions(SelectOption.of(
                        "Remove: " + truncate(questions.get(index).prompt(), 90), Integer.toString(index)));
            }
            children.add(ActionRow.of(remove.build()));
        }
        children.add(ActionRow.of(
                Button.primary(id(panel, Page.QUESTIONS, "qadd", categoryArg), "Add question")
                        .withDisabled(questions.size() >= TicketQuestion.MAX_QUESTIONS),
                Button.secondary(id(panel, Page.QUESTIONS, "back"), "Back to categories")));
    }

    /**
     * The add/edit form for one question. {@code question} is null when adding. Short/Paragraph is a
     * radio group and Required a checkbox, so nothing has to be typed for those settings.
     */
    private Modal questionModal(TicketPanelData panel, int categoryIndex, String questionKey, TicketQuestion question) {
        boolean paragraph = question != null && question.paragraph();
        return Modal.create(id(panel, Page.QUESTIONS, "qmodal", categoryIndex + ":" + questionKey),
                        question == null ? "Add a question" : "Edit question")
                .addComponents(
                        Label.of("Question", input("prompt", TextInputStyle.SHORT,
                                TicketQuestion.MAX_PROMPT_LENGTH, question == null ? null : question.prompt(),
                                "What is your order number?", true)),
                        Label.of("Placeholder (optional)", input("placeholder", TextInputStyle.SHORT,
                                TicketQuestion.MAX_PLACEHOLDER_LENGTH, question == null ? null : question.placeholder(),
                                "Grey hint text inside the box", false)),
                        Label.of("Maximum length (optional)", input("maxlength", TextInputStyle.SHORT, 4,
                                question == null || question.maxLength() == null ? null : question.maxLength().toString(),
                                "1 to " + TicketQuestion.MAX_ANSWER_LENGTH + " (default " + TicketQuestion.MAX_ANSWER_LENGTH + ")",
                                false)),
                        Label.of("Answer box", "Short is one line, paragraph allows several",
                                RadioGroup.create("style")
                                        .addOption("Short", "SHORT", "A single line")
                                        .addOption("Paragraph", "PARAGRAPH", "Several lines")
                                        .setSelectedValue(paragraph ? "PARAGRAPH" : "SHORT")
                                        .build()),
                        Label.of("Required", "Members must answer to open a ticket",
                                Checkbox.of("required", question == null || question.required())))
                .build();
    }

    private static Section row(String buttonId, String text) {
        return Section.of(Button.secondary(buttonId, "Edit"), TextDisplay.of(text));
    }

    private static String mentionList(List<String> ids, String prefix, Guild guild, boolean roles) {
        List<String> valid = ids.stream()
                .filter(value -> roles ? guild.getRoleById(value) != null : guild.getGuildChannelById(value) != null)
                .toList();
        if (valid.isEmpty()) {
            return "*none*";
        }
        return truncate(String.join(" ", valid.stream().map(value -> "<" + prefix + value + ">").toList()), 600);
    }

    // ---------------------------------------------------------------------------------------
    // Interaction plumbing
    // ---------------------------------------------------------------------------------------

    /** Validates the click and loads the panel; replies with an error and returns null on failure. */
    private Ctx resolve(IReplyCallback event, String customId) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            deny(event, "This editor can only be used inside a server.");
            return null;
        }
        if (!TicketPermissions.canManagePanels(member)) {
            deny(event, "You don't have permission to edit ticket panels.");
            return null;
        }
        String[] parts = customId.split(":", 5);
        if (parts.length < 4) {
            deny(event, "That editor control is invalid. Reopen the editor with /ticket panel edit.");
            return null;
        }
        try {
            int panelId = Integer.parseInt(parts[1]);
            TicketPanelData panel = Database.getPanelDataById(guild.getId(), panelId);
            if (panel == null) {
                deny(event, "That ticket panel no longer exists.");
                return null;
            }
            return new Ctx(panel, Page.fromKey(parts[2]), parts[3], parts.length > 4 ? parts[4] : null);
        } catch (NumberFormatException exception) {
            deny(event, "Invalid ticket panel ID.");
        } catch (SQLException exception) {
            exception.printStackTrace();
            deny(event, "Couldn't load the ticket panel. Please try again.");
        }
        return null;
    }

    private static void deny(IReplyCallback event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    private void refresh(IMessageEditCallback event, TicketPanelData panel, Page page, String notice) {
        event.editMessage(view(panel, page, event.getGuild(), notice)).queue();
    }

    private void refreshQuestions(IMessageEditCallback event, TicketPanelData panel, int categoryIndex, String notice) {
        event.editMessage(questionsView(panel, categoryIndex, event.getGuild(), notice)).queue();
    }

    /** Parses "categoryIndex" or "categoryIndex:questionIndex" from a component ID argument; null if invalid. */
    private static int[] parseIndexes(String arg, int expected) {
        if (arg == null) {
            return null;
        }
        String[] parts = arg.split(":");
        if (parts.length < expected) {
            return null;
        }
        try {
            int[] indexes = new int[expected];
            for (int i = 0; i < expected; i++) {
                indexes[i] = "new".equals(parts[i]) ? -1 : Integer.parseInt(parts[i]);
            }
            return indexes;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private <E extends IMessageEditCallback & IReplyCallback> void saveAndRefresh(E event, Ctx ctx, String notice) {
        try {
            Database.savePanel(ctx.panel());
        } catch (SQLException exception) {
            exception.printStackTrace();
            deny(event, "Couldn't save that change. Please try again.");
            return;
        }
        refresh(event, ctx.panel(), ctx.page(), notice);
    }

    // ---------------------------------------------------------------------------------------
    // Buttons
    // ---------------------------------------------------------------------------------------

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith(PREFIX)) {
            return;
        }
        Ctx ctx = resolve(event, event.getComponentId());
        if (ctx == null) {
            return;
        }
        TicketPanelData panel = ctx.panel();
        Page page = ctx.page();

        switch (ctx.action()) {
            case "title" -> event.replyModal(Modal.create(id(panel, page, "title"), "Title & description")
                    .addComponents(
                            Label.of("Title", input("title", TextInputStyle.SHORT, 256, panel.embedTitle,
                                    "Leave blank to use the panel name: " + truncate(panel.name, 40), false)),
                            Label.of("Description", input("description", TextInputStyle.PARAGRAPH, 2000,
                                    panel.content, "Shown above the create button", false)))
                    .build()).queue();
            case "colour" -> event.replyModal(Modal.create(id(panel, page, "colour"), "Colour")
                    .addComponents(Label.of("Hex colour", input("colour", TextInputStyle.SHORT, 7,
                            panel.embedColour, "#5865F2 (leave blank for the default)", false)))
                    .build()).queue();
            case "images" -> event.replyModal(Modal.create(id(panel, page, "images"), "Images")
                    .addComponents(
                            Label.of("Image URL", input("image", TextInputStyle.SHORT, 500, panel.imageUrl,
                                    "Large image under the description (https://...)", false)),
                            Label.of("Thumbnail URL", input("thumbnail", TextInputStyle.SHORT, 500,
                                    panel.thumbnailUrl, "Small image in the corner (https://...)", false)))
                    .build()).queue();
            case "footer" -> event.replyModal(Modal.create(id(panel, page, "footer"), "Footer")
                    .addComponents(Label.of("Footer text", input("footer", TextInputStyle.SHORT, 200,
                            panel.footerText, "Leave blank to show the server name", false)))
                    .build()).queue();
            case "button" -> event.replyModal(Modal.create(id(panel, page, "button"), "Create button")
                    .addComponents(
                            Label.of("Label", input("label", TextInputStyle.SHORT, 80, panel.buttonLabel,
                                    TicketPanelData.DEFAULT_BUTTON_LABEL, false)),
                            Label.of("Emoji", input("emoji", TextInputStyle.SHORT, 80, panel.buttonEmoji,
                                    "🎫 or a server emoji like <:name:id>", false)),
                            Label.of("Colour", input("style", TextInputStyle.SHORT, 10,
                                    panel.buttonStyle == null ? null : styleName(panel.buttonStyle),
                                    "Blue, Grey, Green or Red", false)))
                    .build()).queue();
            case "categories" -> event.replyModal(Modal.create(id(panel, page, "categories"), "Categories")
                    .addComponents(Label.of("Categories (one per line, up to 25)",
                            input("categories", TextInputStyle.PARAGRAPH, 2500,
                                    String.join("\n", panel.categories), "Billing\nSupport\nReport a user", false)))
                    .build()).queue();
            case "back" -> refresh(event, panel, Page.CATEGORIES, null);
            case "qadd" -> openQuestionModal(event, ctx, true);
            case "qedit" -> openQuestionModal(event, ctx, false);
            case "preview" -> preview(event, panel);
            case "publish" -> publish(event, ctx);
            case "delete" -> event.editMessage(deleteConfirmation(panel, page)).queue();
            case "delete_cancel" -> refresh(event, panel, page, null);
            case "delete_confirm" -> deletePanel(event, ctx);
            case "done" -> event.editMessage(plainView(
                    "**Editor closed.** Your changes are saved. Reopen it any time with `/ticket panel edit`."))
                    .queue();
            default -> deny(event, "That editor control isn't available.");
        }
    }

    private void openQuestionModal(ButtonInteractionEvent event, Ctx ctx, boolean adding) {
        TicketPanelData panel = ctx.panel();
        int[] indexes = parseIndexes(ctx.arg(), adding ? 1 : 2);
        if (indexes == null || indexes[0] < 0 || indexes[0] >= panel.categories.size()) {
            deny(event, "That category no longer exists. Reopen the editor and try again.");
            return;
        }
        int categoryIndex = indexes[0];
        List<TicketQuestion> questions;
        try {
            questions = Database.getPanelQuestions(panel.id, panel.categories.get(categoryIndex));
        } catch (SQLException exception) {
            exception.printStackTrace();
            deny(event, "Couldn't load that category's questions. Please try again.");
            return;
        }
        if (adding) {
            if (questions.size() >= TicketQuestion.MAX_QUESTIONS) {
                refreshQuestions(event, panel, categoryIndex, "⚠️ A category can have at most "
                        + TicketQuestion.MAX_QUESTIONS + " questions.");
                return;
            }
            event.replyModal(questionModal(panel, categoryIndex, "new", null)).queue();
            return;
        }
        int questionIndex = indexes[1];
        if (questionIndex < 0 || questionIndex >= questions.size()) {
            refreshQuestions(event, panel, categoryIndex, "⚠️ That question no longer exists.");
            return;
        }
        event.replyModal(questionModal(panel, categoryIndex, Integer.toString(questionIndex),
                questions.get(questionIndex))).queue();
    }

    private void preview(ButtonInteractionEvent event, TicketPanelData panel) {
        TicketPanel renderer = new TicketPanel();
        event.reply("**Preview.** This is how members will see the panel (the controls are disabled here).")
                .addEmbeds(renderer.buildEmbed(panel, event.getGuild()))
                .addComponents(renderer.buildActionRow(panel).withDisabled(true))
                .setEphemeral(true)
                .queue();
    }

    private void publish(ButtonInteractionEvent event, Ctx ctx) {
        TicketPanelData panel = ctx.panel();
        Guild guild = event.getGuild();
        TextChannel channel = panel.channelId == null ? null : guild.getTextChannelById(panel.channelId);
        if (channel == null) {
            refresh(event, panel, ctx.page(), "⚠️ Select a panel channel on the **Roles & routing** page first.");
            return;
        }

        event.deferEdit().queue(hook -> new TicketPanel().publish(panel, channel).queue(
                message -> {
                    try {
                        Database.savePanelMessage(panel.id, channel.getId(), message.getId());
                        panel.channelId = channel.getId();
                        panel.messageId = message.getId();
                    } catch (SQLException exception) {
                        exception.printStackTrace();
                        hook.editOriginal(view(panel, ctx.page(), guild,
                                "⚠️ The panel was sent but I couldn't remember its message, so it can't be updated in place."))
                                .queue();
                        return;
                    }
                    hook.editOriginal(view(panel, ctx.page(), guild,
                            "✅ Published in " + channel.getAsMention() + ".")).queue();
                },
                error -> hook.editOriginal(view(panel, ctx.page(), guild,
                        "⚠️ I couldn't publish the panel. Check that I can view, send messages and embed links in "
                                + channel.getAsMention() + ".")).queue()));
    }

    private MessageEditData deleteConfirmation(TicketPanelData panel, Page page) {
        Container container = Container.of(
                TextDisplay.of("## Delete **" + truncate(panel.name, 60) + "**?"),
                TextDisplay.of("This permanently deletes the panel with its categories, questions and roles, "
                        + "and removes the published panel message.\n"
                        + "Tickets that are already open keep working, but only members with Manage Channels or "
                        + "Manage Server can manage them afterwards."),
                ActionRow.of(
                        Button.danger(id(panel, page, "delete_confirm"), "Delete panel"),
                        Button.secondary(id(panel, page, "delete_cancel"), "Cancel")));
        return new MessageEditBuilder().useComponentsV2().setComponents(container).build();
    }

    private void deletePanel(ButtonInteractionEvent event, Ctx ctx) {
        TicketPanelData panel = ctx.panel();
        Guild guild = event.getGuild();
        try {
            Database.deletePanel(guild.getId(), panel.id);
        } catch (SQLException exception) {
            exception.printStackTrace();
            refresh(event, panel, ctx.page(), "⚠️ Couldn't delete the panel. Please try again.");
            return;
        }
        if (panel.channelId != null && panel.messageId != null) {
            TextChannel channel = guild.getTextChannelById(panel.channelId);
            if (channel != null) {
                channel.deleteMessageById(panel.messageId).queue(null, error -> { });
            }
        }
        event.editMessage(plainView("**Deleted** the panel `" + truncate(panel.name, 60) + "`.")).queue();
    }

    // ---------------------------------------------------------------------------------------
    // Select menus
    // ---------------------------------------------------------------------------------------

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith(PREFIX) || event.getValues().isEmpty()) {
            return;
        }
        Ctx ctx = resolve(event, event.getComponentId());
        if (ctx == null) {
            return;
        }
        String value = event.getValues().get(0);
        switch (ctx.action()) {
            case "nav" -> refresh(event, ctx.panel(), Page.fromKey(value), null);
            case "qpick" -> {
                int index;
                try {
                    index = Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    deny(event, "That category isn't available.");
                    return;
                }
                if (index < 0 || index >= ctx.panel().categories.size()) {
                    deny(event, "That category no longer exists. Reopen the editor and try again.");
                    return;
                }
                refreshQuestions(event, ctx.panel(), index, null);
            }
            case "qremove" -> removeQuestion(event, ctx, value);
            default -> deny(event, "That editor control isn't available.");
        }
    }

    private void removeQuestion(StringSelectInteractionEvent event, Ctx ctx, String selected) {
        TicketPanelData panel = ctx.panel();
        int[] indexes = parseIndexes(ctx.arg(), 1);
        int questionIndex;
        try {
            questionIndex = Integer.parseInt(selected);
        } catch (NumberFormatException exception) {
            deny(event, "That question isn't available.");
            return;
        }
        if (indexes == null || indexes[0] < 0 || indexes[0] >= panel.categories.size()) {
            deny(event, "That category no longer exists. Reopen the editor and try again.");
            return;
        }
        int categoryIndex = indexes[0];
        String category = panel.categories.get(categoryIndex);
        try {
            List<TicketQuestion> questions = new ArrayList<>(Database.getPanelQuestions(panel.id, category));
            if (questionIndex < 0 || questionIndex >= questions.size()) {
                refreshQuestions(event, panel, categoryIndex, "⚠️ That question no longer exists.");
                return;
            }
            TicketQuestion removed = questions.remove(questionIndex);
            Database.savePanelQuestions(panel.id, category, questions);
            refreshQuestions(event, panel, categoryIndex, "✅ Removed **" + truncate(removed.prompt(), 45) + "**.");
        } catch (SQLException exception) {
            exception.printStackTrace();
            deny(event, "Couldn't remove that question. Please try again.");
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (!event.getComponentId().startsWith(PREFIX)) {
            return;
        }
        Ctx ctx = resolve(event, event.getComponentId());
        if (ctx == null) {
            return;
        }
        TicketPanelData panel = ctx.panel();
        List<IMentionable> values = new ArrayList<>(event.getValues());

        switch (ctx.action()) {
            case "staff", "ping" -> {
                List<String> roleIds = values.stream()
                        .filter(Role.class::isInstance)
                        .map(Role.class::cast)
                        .filter(role -> !role.isPublicRole())
                        .map(Role::getId)
                        .distinct()
                        .toList();
                if ("staff".equals(ctx.action())) {
                    panel.staffRoles = new ArrayList<>(roleIds);
                } else {
                    panel.pingRoles = new ArrayList<>(roleIds);
                }
                saveAndRefresh(event, ctx, "✅ Saved.");
            }
            case "ticketcat" -> {
                panel.ticketCategoryId = values.stream()
                        .filter(Category.class::isInstance)
                        .map(Category.class::cast)
                        .map(Category::getId)
                        .findFirst()
                        .orElse(null);
                saveAndRefresh(event, ctx, "✅ Saved.");
            }
            case "channel" -> {
                String newChannelId = values.stream()
                        .filter(TextChannel.class::isInstance)
                        .map(TextChannel.class::cast)
                        .map(TextChannel::getId)
                        .findFirst()
                        .orElse(null);
                boolean moved = panel.messageId != null && !java.util.Objects.equals(newChannelId, panel.channelId);
                if (moved) {
                    // Moving the panel removes the old message; Publish posts it in the new channel.
                    TextChannel old = panel.channelId == null ? null : event.getGuild().getTextChannelById(panel.channelId);
                    if (old != null) {
                        old.deleteMessageById(panel.messageId).queue(null, error -> { });
                    }
                    panel.messageId = null;
                }
                panel.channelId = newChannelId;
                saveAndRefresh(event, ctx, moved
                        ? "✅ Saved. The old panel message was removed. Press **Publish** to post it here."
                        : "✅ Saved.");
            }
            case "transcript" -> {
                panel.transcriptChannelId = values.stream()
                        .filter(TextChannel.class::isInstance)
                        .map(TextChannel.class::cast)
                        .map(TextChannel::getId)
                        .findFirst()
                        .orElse(null);
                saveAndRefresh(event, ctx, "✅ Saved.");
            }
            default -> deny(event, "That editor control isn't available.");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Modals
    // ---------------------------------------------------------------------------------------

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith(PREFIX)) {
            return;
        }
        Ctx ctx = resolve(event, event.getModalId());
        if (ctx == null) {
            return;
        }
        TicketPanelData panel = ctx.panel();

        try {
            switch (ctx.action()) {
                case "title" -> {
                    panel.embedTitle = blankToNull(value(event, "title"));
                    panel.content = blankToNull(value(event, "description"));
                }
                case "colour" -> {
                    String colour = value(event, "colour");
                    if (!colour.isEmpty() && !HEX_COLOUR.matcher(colour).matches()) {
                        throw new IllegalArgumentException("Use a hex colour like `#5865F2`.");
                    }
                    panel.embedColour = colour.isEmpty() ? null : normaliseColour(colour);
                }
                case "images" -> {
                    panel.imageUrl = validUrl(value(event, "image"), "Image");
                    panel.thumbnailUrl = validUrl(value(event, "thumbnail"), "Thumbnail");
                }
                case "footer" -> panel.footerText = blankToNull(value(event, "footer"));
                case "button" -> {
                    panel.buttonLabel = blankToNull(value(event, "label"));
                    String emoji = value(event, "emoji");
                    if (!emoji.isEmpty() && TicketPanel.parseEmoji(emoji) == null) {
                        throw new IllegalArgumentException(
                                "I couldn't read that emoji. Use a normal emoji or a server emoji like `<:name:id>`.");
                    }
                    panel.buttonEmoji = emoji.isEmpty() ? null : emoji;
                    panel.buttonStyle = parseStyleInput(value(event, "style"));
                }
                case "categories" -> panel.categories = parseCategories(value(event, "categories"));
                case "qmodal" -> {
                    saveQuestion(event, ctx);
                    return;
                }
                default -> {
                    deny(event, "That editor control isn't available.");
                    return;
                }
            }
        } catch (IllegalArgumentException exception) {
            deny(event, exception.getMessage());
            return;
        }
        saveAndRefresh(event, ctx, "✅ Saved.");
    }

    /** Saves the add/edit question form; the argument is "categoryIndex:questionIndex" or "categoryIndex:new". */
    private void saveQuestion(ModalInteractionEvent event, Ctx ctx) {
        TicketPanelData panel = ctx.panel();
        int[] indexes = parseIndexes(ctx.arg(), 2);
        if (indexes == null || indexes[0] < 0 || indexes[0] >= panel.categories.size()) {
            deny(event, "That category no longer exists. Reopen the editor and try again.");
            return;
        }
        int categoryIndex = indexes[0];
        int questionIndex = indexes[1];
        String category = panel.categories.get(categoryIndex);

        TicketQuestion question;
        try {
            question = readQuestion(event);
        } catch (IllegalArgumentException exception) {
            deny(event, exception.getMessage());
            return;
        }

        try {
            List<TicketQuestion> questions = new ArrayList<>(Database.getPanelQuestions(panel.id, category));
            boolean adding = questionIndex < 0;
            if (adding) {
                if (questions.size() >= TicketQuestion.MAX_QUESTIONS) {
                    refreshQuestions(event, panel, categoryIndex, "⚠️ A category can have at most "
                            + TicketQuestion.MAX_QUESTIONS + " questions.");
                    return;
                }
                questions.add(question);
            } else {
                if (questionIndex >= questions.size()) {
                    refreshQuestions(event, panel, categoryIndex, "⚠️ That question no longer exists.");
                    return;
                }
                questions.set(questionIndex, question);
            }
            Database.savePanelQuestions(panel.id, category, questions);
            refreshQuestions(event, panel, categoryIndex,
                    (adding ? "✅ Added **" : "✅ Saved **") + truncate(question.prompt(), 45) + "**.");
        } catch (SQLException exception) {
            exception.printStackTrace();
            deny(event, "Couldn't save the question. Please try again.");
        }
    }

    /** Reads and validates the question form. */
    private static TicketQuestion readQuestion(ModalInteractionEvent event) {
        String prompt = value(event, "prompt");
        if (prompt.isEmpty()) {
            throw new IllegalArgumentException("A question needs some text.");
        }
        if (prompt.length() > TicketQuestion.MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException("A question can be at most "
                    + TicketQuestion.MAX_PROMPT_LENGTH + " characters (a Discord limit).");
        }
        String placeholder = blankToNull(value(event, "placeholder"));
        if (placeholder != null && placeholder.length() > TicketQuestion.MAX_PLACEHOLDER_LENGTH) {
            throw new IllegalArgumentException("A placeholder can be at most "
                    + TicketQuestion.MAX_PLACEHOLDER_LENGTH + " characters.");
        }

        Integer maxLength = null;
        String maxLengthText = value(event, "maxlength");
        if (!maxLengthText.isEmpty()) {
            try {
                maxLength = Integer.parseInt(maxLengthText);
            } catch (NumberFormatException exception) {
                maxLength = 0;
            }
            if (maxLength < 1 || maxLength > TicketQuestion.MAX_ANSWER_LENGTH) {
                throw new IllegalArgumentException("Maximum length must be a number from 1 to "
                        + TicketQuestion.MAX_ANSWER_LENGTH + ".");
            }
        }

        var style = event.getValue("style");
        boolean paragraph = style != null && "PARAGRAPH".equals(style.getAsOptionalString());
        var required = event.getValue("required");
        return new TicketQuestion(prompt, paragraph, required != null && required.getAsBoolean(),
                placeholder, maxLength);
    }

    // ---------------------------------------------------------------------------------------
    // Parsing and small helpers
    // ---------------------------------------------------------------------------------------

    private static TextInput input(
            String id, TextInputStyle style, int maxLength, String value, String placeholder, boolean required) {
        TextInput.Builder builder = TextInput.create(id, style)
                .setRequired(required)
                .setMaxLength(maxLength);
        if (placeholder != null) {
            builder.setPlaceholder(truncate(placeholder, 100));
        }
        if (value != null && !value.isBlank()) {
            builder.setValue(value.length() > maxLength ? value.substring(0, maxLength) : value);
        }
        return builder.build();
    }

    private static String value(ModalInteractionEvent event, String id) {
        var mapping = event.getValue(id);
        return mapping == null ? "" : mapping.getAsString().trim();
    }

    private static String validUrl(String value, String what) {
        if (value.isEmpty()) {
            return null;
        }
        if (!HTTP_URL.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be a link starting with http:// or https://.");
        }
        return value;
    }

    private static String parseStyleInput(String value) {
        if (value.isEmpty()) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "blue", "primary" -> "PRIMARY";
            case "grey", "gray", "secondary" -> "SECONDARY";
            case "green", "success" -> "SUCCESS";
            case "red", "danger" -> "DANGER";
            default -> throw new IllegalArgumentException("Button colour must be Blue, Grey, Green or Red.");
        };
    }

    private static String styleName(String style) {
        if (style == null) {
            return "Blue";
        }
        return switch (style.toUpperCase(Locale.ROOT)) {
            case "SECONDARY" -> "Grey";
            case "SUCCESS" -> "Green";
            case "DANGER" -> "Red";
            default -> "Blue";
        };
    }

    private static String normaliseColour(String colour) {
        return "#" + (colour.startsWith("#") ? colour.substring(1) : colour).toUpperCase(Locale.ROOT);
    }

    private static List<String> parseCategories(String value) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        for (String line : value.split("[\\r\\n]+")) {
            String category = line.trim();
            if (category.isEmpty()) {
                continue;
            }
            if (category.length() > 100) {
                throw new IllegalArgumentException("Each category must be 100 characters or fewer.");
            }
            categories.add(category);
        }
        if (categories.size() > 25) {
            throw new IllegalArgumentException("A ticket panel can have at most 25 categories.");
        }
        return new ArrayList<>(categories);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
