package org.gradle.sample.app;

import com.google.gson.Gson;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.gradle.sample.app.data.Message;

public class Main {

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("json", true, "data to parse");
        options.addOption("debug", false, "prints module infos");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("debug")) {
            printModuleDebug(Main.class);
            printModuleDebug(Gson.class);
            printModuleDebug(StringUtils.class);
            printModuleDebug(CommandLine.class);
            printModuleDebug(BeanUtils.class);
        }

        String json = cmd.getOptionValue("json");
        Message message = new Gson().fromJson(json == null ? "{}" : json, Message.class);

        Object copy = BeanUtils.cloneBean(message);
        System.out.println();
        System.out.println("Original: " + copy.toString());
        System.out.println("Copy:     " + copy.toString());

    }

    private static void printModuleDebug(Class<?> clazz) {
        System.out.println(clazz.getModule().getName() + " - " + clazz.getModule().getDescriptor().version().get());
    }

}
