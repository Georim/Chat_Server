package ru.georimstudio;

/**
 * Created by Oberon on 02.06.2015.
 */

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.io.*;
import java.net.ServerSocket;
import java.util.Iterator;
import java.util.Set;

public class NetListener implements Runnable {
    private static int PORT = 9876;
    private static int BUFFER_SIZE = 2048;

    public void run() {
        Selector selector = null;
        ServerSocket serverSocket = null;
        //выделяем буфер... почему именно директ????
        ByteBuffer sharedBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        try {
            // Создание Канала
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

            //отлючение блокировок канала
            serverSocketChannel.configureBlocking(false);

            // берем сокет сервера из канала
            serverSocket = serverSocketChannel.socket();

            //создаем свой адрес указывая номер порта
            InetSocketAddress inetSocketAddress = new InetSocketAddress(PORT);
            System.out.println("Address: " + inetSocketAddress.toString());

            //привязываем СерверСокет на этот адрес
            serverSocket.bind(inetSocketAddress);

            //Создание селектора
            selector = Selector.open();

            //регистрация селектора на канале
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            System.err.println("Unable to setup environment");
            System.exit(-1);
        }
        try {
            while (true) {
                int count = selector.select();
                // нечего обрабатывать
                if (count == 0) {
                    continue;
                }
                // набор соединений которым есть что сказать (OP_ACCEPT)
                Set keySet = selector.selectedKeys();

                // организуем итератор для перебора по необработанным ключам(почему не foreach???)
                // что будет если добавится новый ключ, пока мы обрабатываем старые? или не добавится?
                Iterator itor = keySet.iterator();

                while (itor.hasNext()) {
                    //выбираем конкретный ключ, соответсвующий соединению с клиентом
                    SelectionKey selectionKey = (SelectionKey) itor.next();

                    //удаляем из списка необработанных ключей
                    itor.remove();

                    //объявляем для него сокет
                    Socket socket = null;
                    SocketChannel channel = null;

                    //проверяем что соединение имеется
                    if (selectionKey.isAcceptable()) {
                        System.out.println("Got acceptable key");
                        try {
                            //создаем сокет для этого соединения
                            socket = serverSocket.accept();
                            System.out.println
                                    ("Connection from: " + socket);
                            channel = socket.getChannel();
                        } catch (IOException e) {
                            System.err.println("Unable to accept channel");
                            e.printStackTrace();
                            selectionKey.cancel();
                        }
                        if (channel != null) {
                            try {
                                System.out.println
                                        ("Watch for something to read");
                                channel.configureBlocking(false);
                                channel.register
                                        (selector, SelectionKey.OP_READ);
                            } catch (IOException e) {
                                System.err.println("Unable to use channel");
                                e.printStackTrace();
                                selectionKey.cancel();
                            }
                        }
                    }
                    if (selectionKey.isReadable()) {
                        System.out.println("Reading channel");
                        //Организуем канал
                        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                        //подготавливаем буфер
                        sharedBuffer.clear();
                        int bytes = -1;
                        try {

                            while ((bytes = socketChannel.read(sharedBuffer)) > 0) {
                                //читаем в буффер из канала
                                System.out.println("Reading...");
                                // закончили читать, подготавливаем буффер для чтения из него
                                sharedBuffer.flip();

                                //пишем в канал
                                while (sharedBuffer.hasRemaining()) {
                                    System.out.println("Writing...");
                                    socketChannel.write(sharedBuffer);
                                }
                                sharedBuffer.clear();
                            }
                        } catch (IOException e) {
                            System.err.println("Error writing back bytes");
                            e.printStackTrace();
                            selectionKey.cancel();
                        }
                        try {
                            System.out.println("Closing...");
                            //закрываем буффер
                            socketChannel.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                            selectionKey.cancel();
                        }
                    }
                    System.out.println("Next...");
                }
            }

        } catch (IOException e) {
            System.err.println("Error during select()");
            e.printStackTrace();
        }
    }
}
