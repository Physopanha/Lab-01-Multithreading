package edu.itc.se.Lab01.task3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

record Order( int id, String item, int qty){}
interface OrderQueue {
    void put(Order order) throws InterruptedException;
    Order take() throws InterruptedException;
}
final class BlockingOrderQueue implements OrderQueue{
    private final java.util.concurrent.BlockingQueue<Order> q;

    public BlockingOrderQueue(int capacity) {
        this.q = new java.util.concurrent.ArrayBlockingQueue<>(capacity);
    }
    @Override 
    public void put(Order o) throws InterruptedException{
        q.put(o);
    }
    @Override 
    public Order take() throws  InterruptedException{
    return q.take();
    }
    
}

class BoundedBuffer implements OrderQueue{
    private final java.util.ArrayDeque<Order> items = new java.util.ArrayDeque<>();
    private final int capacity;

    public BoundedBuffer(int capacity){
        this.capacity = capacity;
    }

    @Override
    public synchronized void put(Order order) throws InterruptedException{
        while (items.size() == capacity){
            wait();
        }
        items.addLast(order);
        notifyAll();
    }
    @Override
    public synchronized Order take() throws InterruptedException{
        while (items.isEmpty()){
            wait();
        }
        Order order = items.removeFirst();
        notifyAll();
        return order;
    }
} 
class Producer implements Runnable{
    private final OrderQueue queue;
    private final int startId;
    private final int numOrders;

    public Producer(OrderQueue queue, int startId, int numOrders){
        this.queue = queue;
        this.startId = startId;
        this.numOrders = numOrders;
    }

    @Override
    public void run(){
        try{
            for (int i = 0; i < numOrders; i++){
                int id = startId + i ;
                int qty = ThreadLocalRandom.current().nextInt(1,6);
                queue.put(new Order(id, "item-" + id, qty));
                Thread.sleep(ThreadLocalRandom.current().nextInt(0,21));
            }
        } catch  (InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }
}
class Consumer implements Runnable{
    private final OrderQueue queue;
    private final String name;
    private final AtomicInteger processedCount;

    public Consumer(OrderQueue queue, String name, AtomicInteger processedCount){
        this.queue = queue;
        this.name = name;
        this.processedCount = processedCount;
    }

    @Override
    public void run(){
        try {
            while (true) { 
                Order order = queue.take();
                if (order.id() == -1){ 
                    System.out.println(name + " received poison pill, stopping");
                    break;
                }
                Thread.sleep(ThreadLocalRandom.current().nextInt(5,31));
                System.out.println(name + " processed Order [id=" + order.id() + ", item="+ order.item() + ", qty="+ order.qty() + "]");
                processedCount.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
class Simulation {
    static void run(OrderQueue queue) throws InterruptedException{
        long start = System.currentTimeMillis();
        AtomicInteger processedCount = new AtomicInteger(0);
        
        Thread p1 = new Thread(new Producer(queue,1,50));
        Thread p2 = new Thread(new Producer(queue,51,50));

        Thread c1 = new Thread(new Consumer(queue, "consumer-1", processedCount));
        Thread c2 = new Thread(new Consumer(queue, "consumer-2", processedCount));
        Thread c3 = new Thread(new Consumer(queue, "consumer-3", processedCount));

        p1.start();
        p2.start();
        c1.start();
        c2.start();
        c3.start();

        p1.join();
        p2.join();

        for (int i =0 ; i<3; i++){
            queue.put(new Order(-1, "poison", 0));
        }

        c1.join();
        c2.join();
        c3.join();

        long ms = System.currentTimeMillis() - start;
        System.out.println("Total processed: "+ processedCount.get()+" (expetced 100) in " + ms +"ms");
        assert processedCount.get() == 100: "Failed: Did not process exactly 100 orders!";
    }
}
public class main {
    public static void main(String[] args) throws InterruptedException {
        Simulation.run(new BoundedBuffer(5));
    }
}
class Main2 {
    public static void main(String[] args) throws InterruptedException {
        Simulation.run(new BlockingOrderQueue(5));
    }
}