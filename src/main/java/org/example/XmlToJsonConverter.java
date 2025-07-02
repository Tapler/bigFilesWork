package org.example;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileWriter;

// Конвертер XML в JSON много памяти жрет
public class XmlToJsonConverter {
    public static void main(String[] args) throws Exception {
        String xmlPath = "output_1.xml";
        String jsonPath = "output_1.json";

        // Парсинг XML
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new File(xmlPath));
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement(); // PaymentsRegistrySegment

        // Корневые поля
        JSONObject json = new JSONObject();
        json.put("RegistryUID", root.getAttribute("RegistryUID"));
        json.put("RegistryDate", root.getAttribute("RegistryDate"));
        json.put("RegistrySum", root.getAttribute("RegistrySum"));
        json.put("RegistryType", root.getAttribute("RegistryType"));
        json.put("DeliveryOrganization", root.getAttribute("DeliveryOrganization"));
        json.put("registryAmount", root.getAttribute("RegistrySum"));
        // Можно добавить другие поля, если нужно

        // Массив платежей
        JSONArray paymentRegistryItem = new JSONArray();
        NodeList payments = root.getElementsByTagName("Payment");
        for (int i = 0; i < payments.getLength(); i++) {
            Node node = payments.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                JSONObject payment = new JSONObject();
                payment.put("RegistryStringUID", el.getAttribute("RegistryStringUID"));
                payment.put("PaymentType", el.getAttribute("PaymentType"));
                payment.put("PaymentSum", el.getAttribute("PaymentSum"));
                payment.put("PaymentPurpose", el.getAttribute("PaymentPurpose"));
                payment.put("PaymentOrder", el.getAttribute("PaymentOrder"));
                payment.put("OperationKind", el.getAttribute("OperationKind"));
                payment.put("ReceiverBankBic", el.getAttribute("ReceiverBankBic"));
                payment.put("ReceiverCorrAccount", el.getAttribute("ReceiverCorrAccount"));
                payment.put("ReceiverINN", el.getAttribute("ReceiverINN"));
                payment.put("ReceiverAccountNumber", el.getAttribute("ReceiverAccountNumber"));
                payment.put("ReceiverName", el.getAttribute("ReceiverName"));
                paymentRegistryItem.put(payment);
            }
        }
        json.put("paymentRegistryItem", paymentRegistryItem);

        // Запись в файл
        try (FileWriter writer = new FileWriter(jsonPath)) {
            writer.write(json.toString(2));
        }
        System.out.println("JSON успешно создан: " + jsonPath);
    }
}
