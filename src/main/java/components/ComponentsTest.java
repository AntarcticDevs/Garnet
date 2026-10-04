/*
package components;

import commands.Command;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;

import net.dv8tion.jda.api.components.thumbnail.Thumbnail;


public class ComponentsTest extends ListenerAdapter implements Command {

    @Override
    public CommandData getCommandData() {
        return Commands.slash("components-test", "Sends a Components V2 section");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("components-test")) {
            return;
        }

        MediaGalleryItem image = MediaGalleryItem.fromUrl(
        "https://cdn.discordapp.com/attachments/1543925274213752855/1555988593510580224/Designer.png?backend=b2&ex=6ac286cd&is=6ac1354d&hm=584fd886a7edb8c391f539905a0d0a1526b0ca414b2cdbeaf67c7f4de23271d3&"
);

        MediaGallery gallery = MediaGallery.of(image);

        Section section = Section.of(
            Thumbnail.fromUrl("https://cdn.discordapp.com/attachments/1543925274213752855/1555982521009967275/Garnet.png?backend=b2&ex=6ac28125&is=6ac12fa5&hm=c7db4d95dd5ea235683c449ed883de64513ffa92466db4c99c3673af61f22056&"),
            TextDisplay.of("# Some text")
        );

        
        Container container = Container.of(
                section,
                Separator.createDivider(Separator.Spacing.LARGE),
                TextDisplay.of("-# Some text"),
                gallery);

        MessageCreateBuilder message = new MessageCreateBuilder()
                .useComponentsV2()
                .addComponents(container);

        event.reply(message.build()).queue();
    }
}
*/
