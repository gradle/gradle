package com.example.myproduct.server;

import com.example.myproduct.user.table.TableBuilder;
import org.apache.juneau.html.HtmlSerializer;
import org.apache.juneau.serializer.SerializeException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyProductController {

    @RequestMapping("/")
    public String index() throws SerializeException {
        return HtmlSerializer.DEFAULT.serialize(TableBuilder.build());
    }

}
