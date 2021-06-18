package com.redhat.idaas.connect.bluebutton;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.SimpleBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.model.rest.RestBindingMode;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.arc.config.ConfigProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;



public class BlueButtonResource extends RouteBuilder{

        
    public void configure() throws Exception {

        String BLUEBUTTON_CLIENT_ID = org.eclipse.microprofile.config.ConfigProvider.getConfig().getValue("bluebutton.client.id", String.class);
        String BLUEBUTTON_LOCALHOST_NAME = org.eclipse.microprofile.config.ConfigProvider.getConfig().getValue("bluebutton.localhost.name", String.class);
        String BLUEBUTTON_LOCALHOST_CALLBACK_PATH = org.eclipse.microprofile.config.ConfigProvider.getConfig().getValue("bluebutton.localhost.callback.path", String.class);
        String BLUEBUTTON_LOCALHOST_PORT = org.eclipse.microprofile.config.ConfigProvider.getConfig().getValue("bluebutton.localhost.port", String.class);
        String BLUEBUTTON_CLIENT_SECRET = org.eclipse.microprofile.config.ConfigProvider.getConfig().getValue("bluebutton.client.secret", String.class);


        /*
         *   HIDN
         *   HIDN - Health information Data Network
         *   Intended to enable simple movement of data aside from specific standards
         *   Common Use Cases are areas to support remote (iOT/Edge) and any other need for small footprints to larger
         *   footprints
         * : Unstructured data, st
         */
        from("direct:hidn")
            .routeId("HIDN Processing")
            .setHeader("messageprocesseddate").simple("${date:now:yyyy-MM-dd}")
            .setHeader("messageprocessedtime").simple("${date:now:HH:mm:ss:SSS}")
            .setHeader("eventdate").simple("eventdate")
            .setHeader("eventtime").simple("eventtime")
            .setHeader("processingtype").exchangeProperty("processingtype")
            .setHeader("industrystd").exchangeProperty("industrystd")
            .setHeader("component").exchangeProperty("componentname")
            .setHeader("processname").exchangeProperty("processname")
            .setHeader("organization").exchangeProperty("organization")
            .setHeader("careentity").exchangeProperty("careentity")
            .setHeader("customattribute1").exchangeProperty("customattribute1")
            .setHeader("customattribute2").exchangeProperty("customattribute2")
            .setHeader("customattribute3").exchangeProperty("customattribute3")
            .setHeader("camelID").exchangeProperty("camelID")
            .setHeader("exchangeID").exchangeProperty("exchangeID")
            .setHeader("internalMsgID").exchangeProperty("internalMsgID")
            .setHeader("bodyData").exchangeProperty("bodyData")
            .setHeader("bodySize").exchangeProperty("bodySize")
            .convertBodyTo(String.class)
        .to("kafka:topic={{bluebutton.kafka.topic.hidn}}?brokers={{bluebutton.kafka.bootstrap.url}}");
                

                /*
         * Audit
         *
         * Direct component within platform to ensure we can centralize logic
         * There are some values we will need to set within every route
         * We are doing this to ensure we dont need to build a series of beans
         * and we keep the processing as lightweight as possible
         *
         */
        from("direct:auditing")
            .routeId("KIC-KnowledgeInsightConformance")
            .setHeader("messageprocesseddate").simple("${date:now:yyyy-MM-dd}")
            .setHeader("messageprocessedtime").simple("${date:now:HH:mm:ss:SSS}")
            .setHeader("processingtype").exchangeProperty("processingtype")
            .setHeader("industrystd").exchangeProperty("industrystd")
            .setHeader("component").exchangeProperty("componentname")
            .setHeader("messagetrigger").exchangeProperty("messagetrigger")
            .setHeader("processname").exchangeProperty("processname")
            .setHeader("auditdetails").exchangeProperty("auditdetails")
            .setHeader("camelID").exchangeProperty("camelID")
            .setHeader("exchangeID").exchangeProperty("exchangeID")
            .setHeader("internalMsgID").exchangeProperty("internalMsgID")
            .setHeader("bodyData").exchangeProperty("bodyData")
            .convertBodyTo(String.class)
        .to("kafka:topic={{bluebutton.kafka.topic.opsmgmt}}?brokers={{bluebutton.kafka.bootstrap.url}}");



        /*
         *  Logging
         */
        
        from("direct:logging")
            .routeId("Logging")
            .log(LoggingLevel.INFO, log, "HL7 Admissions Message: [${body}]");
        
        

    
        restConfiguration().bindingMode(RestBindingMode.auto)
            .component("platform-http")
            .dataFormatProperty("prettyPrint", "true")
            .contextPath("/").port(8080)
            .apiContextPath("/openapi")
            .apiProperty("api.title", "Camel Quarkus Demo API")
            .apiProperty("api.version", "1.0.0-SNAPSHOT")
            .apiProperty("cors", "true");

        rest().tag("API de serviços Demo utilizando Camel e Quarkus").produces("application/json")

            .get("/bluebutton")				
				.description("Listar todos os clientes")
				.route().routeId("bluebutton-sandbox-auth").to("direct:authorize")
			.endRest()            
            .get("/" + BLUEBUTTON_LOCALHOST_CALLBACK_PATH)				
				.description("Listar todos os clientes")
				.route().routeId("bluebutton-call-back") .to("direct:callback")
			.endRest();

        from("direct:authorize")
            .setHeader("Location", simple("https://sandbox.bluebutton.cms.gov/v1/o/authorize/?response_type=code&client_id=5uCeIboNmAcZRxFr9RQsyEdhffGF9tenOlwvVGCf&redirect_uri=http://localhost:8080/login&scope=patient/Patient.read patient/Coverage.read patient/ExplanationOfBenefit.read profile"))
            .setHeader(Exchange.HTTP_RESPONSE_CODE, simple("302"));

        
            from("direct:callback")
            .process(new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                    String clientId = SimpleBuilder.simple("BSRGaICg4uwezgBo97styPv8OVXxChYDode7Qdw6").evaluate(exchange, String.class);
                    String clientSecret = SimpleBuilder.simple("GDv5I4e3mArJp3SwLQYDYMBcmVP5Fq5NNYQh8kdJtjsMHEw5NnOHHdBqdWH9b3H0jiO18pHqqXvVe3tPe3enV9S5clvpxlcLs6oR7FtEyOVJ4bctr0K4GOqwKh81hiJx").evaluate(exchange, String.class);
                    String code = exchange.getIn().getHeader("code", String.class);
                    String body = "code=" + code + "&grant_type=authorization_code";
                    System.out.println(clientId);
                    String auth = clientId + ":" + clientSecret;
                    String authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                    exchange.getIn().setHeader(Exchange.HTTP_METHOD, "POST");
                    exchange.getIn().setHeader("Authorization", authHeader);
                    exchange.getIn().setHeader("Content-Type", "application/x-www-form-urlencoded");
                    exchange.getIn().setHeader("Content-Length", body.length());
                    exchange.getIn().setBody(body);
                }
            })
            .to("https://sandbox.bluebutton.cms.gov/v1/o/token/?bridgeEndpoint=true")
            .unmarshal(new JacksonDataFormat(ConfigProperties.class))
            .process(new Processor() {
                @Override
                public void process(final Exchange exchange) throws Exception {
                    //final ConfigProperties payload = exchange.getIn().getBody(ConfigProperties.class);
                    //exchange.getIn().setBody(payload.getAccess_token());
                }
            })
            .removeHeader("*")
            .to("direct:start");


     
    }
    
}
