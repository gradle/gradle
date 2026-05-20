package example;

import ratpack.core.server.RatpackServer;

public class App {

    public static void main(String[] args) throws Exception {
        RatpackServer.start(spec -> spec
            .handlers(chain -> chain
                .get(ctx -> ctx.render("Hello World!"))
                .get(":name", ctx -> {
                    String name = ctx.getPathTokens().get("name");
                    ctx.render("Hello " + name + "!");
                })
            )
        );
    }
}
