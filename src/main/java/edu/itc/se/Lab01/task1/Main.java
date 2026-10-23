package edu.itc.se.Lab01.task1;

// Step 1
class Countdown extends Thread{
    public Countdown(String name){
        super(name);
    }
    @Override
    public void run(){
        try{
            for (int i=3; i>0; i--){
                System.out.println(getName()+": "+i);
                Thread.sleep(200);
            }
            System.out.println(getName()+ ": liftoff!");
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }
}
// Step 2
class CountdownTask implements Runnable{
    @Override
    public void run(){
        try {
            for (int i=3; i>0; i--){
                System.out.println(Thread.currentThread().getName()+": "+ i);
                Thread.sleep(200);
            }
            System.out.println(Thread.currentThread().getName()+ ": liftoff!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
public class Main {
    public static void main(String[] args) throws InterruptedException {
        boolean sequential = args.length > 0 && args[0].equals("--sequential");

        // Step 3
        Thread apollo  = new Countdown("apollo");
        Thread gemini  = new Thread(new CountdownTask(), "gemini");
        Thread mercury = new Thread(() -> new CountdownTask().run(), "mercury");
        Thread artemis = Thread.ofVirtual().name("artemis").unstarted(new CountdownTask());

        // Step 5
        if (sequential){
            apollo.run();
            gemini.run();
            mercury.run();
            artemis.run();
        }else{
            apollo.start();
            gemini.start();
            mercury.start();
            artemis.start();
        }
        // TODO: start (or run) all four depending on 'sequential'
        // Step 4
        System.out.println("Mission control: all launched");
        // TODO: join all four
        apollo.join();
        gemini.join();
        mercury.join();
        artemis.join();
        System.out.println("Mission control: all landed");
        // TODO: print name, isVirtual(), getState() of each
        // Step 6
        printThreadInfo(apollo);
        printThreadInfo(gemini);
        printThreadInfo(mercury);
        printThreadInfo(artemis);
    }
    private static void printThreadInfo(Thread t) {
        System.out.printf("%-10s virtual=%-5b state=%s\n", 
            t.getName(), 
            t.isVirtual(), 
            t.getState());
    }
}
