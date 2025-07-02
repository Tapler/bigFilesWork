package org.example;

import java.io.*;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GenerateBig {
    // Количество файлов (сегментов) и платежей в каждом файле настраиваются здесь:
    public static final int FILE_COUNT = 1; // Глобальное количество файлов (SegmentCount)
    public static final int PAYMENT_COUNT = 2_000_000; // Глобальное количество платежей в каждом файле

    public static void main(String[] args) throws IOException {
        // Очистка файлов от прошлого запуска
        for (File f : new File(".").listFiles()) {
            String name = f.getName();
            if (name.matches("output_.*\\.xml") || name.matches("result.*\\.zip") || name.matches("result.*\\.base64")) {
                f.delete();
            }
        }

        Random random = new Random();
        String[] surnames = {"Иванов", "Петров", "Сидоров", "Кузнецов", "Попов", "Соколов", "Лебедев", "Козлов", "Новиков", "Морозов", "Егоров", "Волков", "Соловьёв", "Васильев", "Зайцев", "Павлов", "Семёнов", "Голубев", "Виноградов", "Богданов", "Воробьёв", "Фёдоров", "Михайлов", "Беляев", "Тарасов", "Белов", "Комаров", "Орлов", "Киселёв", "Макаров", "Андреев"};
        String[] names = {"Иван", "Дмитрий", "Павел", "Андрей", "Николай", "Алексей", "Сергей", "Владимир", "Михаил", "Евгений", "Георгий", "Виктор", "Виталий", "Анатолий", "Юрий", "Григорий", "Станислав", "Вячеслав", "Василий", "Аркадий", "Олег", "Артур", "Руслан", "Роман", "Антон", "Игорь", "Ярослав", "Максим", "Пётр", "Егор"};
        String[] patronymics = {"Иванович", "Дмитриевич", "Павлович", "Андреевич", "Николаевич", "Алексеевич", "Сергеевич", "Владимирович", "Михайлович", "Евгеньевич", "Георгиевич", "Викторович", "Витальевич", "Анатольевич", "Юрьевич", "Григорьевич", "Станиславович", "Вячеславович", "Васильевич", "Аркадьевич", "Олегович", "Артурович", "Русланович", "Романович", "Антонович", "Игоревич", "Ярославович", "Максимович", "Петрович", "Егорович"};

        // Двойной проход: сначала считаем сумму, потом генерируем XML
        for (int fileNum = 1; fileNum <= FILE_COUNT; fileNum++) {
            // Первый проход: считаем сумму
            long sum = 0;
            for (int i = 1; i <= PAYMENT_COUNT; i++) {
                int paymentSum = 30000 + random.nextInt(401) * 100;
                sum += paymentSum;
            }
            // Для повторяемости данных во втором проходе создаём новый Random с тем же seed
            random = new Random();

            String registryUID = UUID.randomUUID().toString();
            String registryDate = ZonedDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
            String fileName = "output_big_" + PAYMENT_COUNT + "_" + fileNum + ".xml";
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
                // Запись заголовка XML с уже известной суммой
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writer.write("<PaymentsRegistrySegment xmlns=\"\"\n");
                writer.write("  RegistryUID=\"" + registryUID + "\"\n");
                writer.write("  RegistryDate=\"" + registryDate + "\"\n");
                writer.write("  RegistrySum=\"" + sum + "\"\n");
                writer.write("  SegmentNumber=\"" + fileNum + "\"\n");
                writer.write("  SegmentCount=\"" + FILE_COUNT + "\"\n");
                writer.write("  RegistryType=\"Collecting\"\n");
                writer.write("  DeliveryOrganization=\"PSB\"\n");
                writer.write("  SegmentSum=\"" + sum + "\">\n");

                // Второй проход: генерация и запись платежей
                for (int i = 1; i <= PAYMENT_COUNT; i++) {
                    String paymentPurpose = "Пенсия " + ((fileNum - 1) * PAYMENT_COUNT + i);
                    String receiverBankBic = String.format("%09d", random.nextInt(1_000_000_000));
                    String receiverCorrAccount = "30101810400000000705";
                    String receiverINN = String.format("%014d", Math.abs(random.nextLong()) % 1_000_000_000_000_000L);
                    String receiverAccountNumber = "00012298253454792492";
                    String receiverName = surnames[random.nextInt(30)] + " " + names[random.nextInt(30)] + " " + patronymics[random.nextInt(30)];
                    int paymentSum = 30000 + random.nextInt(401) * 100;

                    Payment payment = new Payment(
                            UUID.randomUUID().toString(),
                            "SocBenefit",
                            paymentSum,
                            paymentPurpose,
                            "01",
                            "01",
                            receiverBankBic,
                            receiverCorrAccount,
                            receiverINN,
                            receiverAccountNumber,
                            receiverName
                    );
                    writer.write(payment.toXml());
                    writer.write("\n");
                }

                writer.write("</PaymentsRegistrySegment>");
            }
            System.out.println("Файл " + fileName + " сгенерирован. Итоговая сумма: " + sum);
        }

        // Сборка всех output_big_*.xml файлов в архив result.zip
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("result.zip"))) {
            for (int fileNum = 1; fileNum <= FILE_COUNT; fileNum++) {
                String fileName = "output_big_" + PAYMENT_COUNT + "_" + fileNum + ".xml";
                File file = new File(fileName);
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(fileName);
                    zos.putNextEntry(zipEntry);
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
        }
        System.out.println("Все файлы собраны в архив result.zip");

        // Запись base64 файла
        byte[] zipBytes = Files.readAllBytes(new File("result.zip").toPath());
        String base64 = Base64.getEncoder().encodeToString(zipBytes);
        try (FileWriter writer = new FileWriter("result.zip.base64")) {
            writer.write(base64);
        }
        System.out.println("Создан файл result.zip.base64 с base64 содержимым архива");
    }
}