package com.chexiang;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;



public class WALLE extends AdvancedRobot{
    private final double CIRCLE = 360;
    private double enemyAbsBearing = 0; //敌方绝对bearing = 我方朝向 + 相对bearing
    private boolean scaned = false;
    private double radarSpeed = 45;
    private ScannedRobotEvent preEvent = new ScannedRobotEvent();
    private double preEnemyEnergy = 100;

    public void setEnemyAbsBearing(double enemyAbsBearing) {
        this.enemyAbsBearing = enemyAbsBearing;
    }

    public double getEnemyAbsBearing() {
        return enemyAbsBearing;
    }

    public void setRadarSpeed(double radarSpeed) {
        this.radarSpeed = radarSpeed;
    }

    public void setPreEvent(ScannedRobotEvent event) {   //深拷贝
        this.preEvent = new ScannedRobotEvent(
                event.getName(),
                event.getEnergy(),
                event.getBearing(),
                event.getDistance(),
                event.getHeading(),
                event.getVelocity(),
                event.isSentryRobot()
        );
    }

    private void reverseRadar(){
        setRadarSpeed(-radarSpeed);
    }

    @Override
    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while(true) {
            out.println("radarSpeed" + radarSpeed);
            turnRadarRight(radarSpeed);
            double radarHeading = getRadarHeading();
            if(scaned && !outOfEnemyRange(radarHeading) && outOfEnemyRange(radarHeading + radarSpeed))
                reverseRadar();


//            ahead(100);
//            turnGunLeft(70);
//            back(100);
            execute();
        }
    }

    private boolean outOfEnemyRange(double radarHeading) {
        radarHeading = Mod(radarHeading);
        return Math.abs( minimizeTurn(radarHeading - getEnemyAbsBearing()) ) > 90;
    }

    @Override
    public void onHitWall(HitWallEvent event) {
        out.println(String.format("onHitWall\n getBearing: %f, getHeading: %f\n" +
                "sum: %f\n", event.getBearing(), getHeading(), Mod(event.getBearing() + getHeading() + CIRCLE / 4)));

        turnAsRefrect(getHeading(), Mod(event.getBearing() + getHeading() + CIRCLE / 4));     //为啥+90度？
    }

    private void turnAsRefrect(double inAngle, double mirrorAngle) {
        turnToTargetAngle( doRefrect(inAngle, mirrorAngle) );
        ahead(50);
    }

    private double doRefrect(double inAngle, double mirrorAngle) {
        if(Math.abs( minimizeTurn(inAngle - mirrorAngle) ) > CIRCLE/4)
            mirrorAngle = reverseAngle(mirrorAngle);
//        out.println(String.format("doRefrect\n inAngle: %f, mirrorAngle: %f\n" +
//                "outAngle: %f\n", inAngle, mirrorAngle, Mod(2*mirrorAngle - inAngle)));
        return Mod(2*mirrorAngle - inAngle);
    }


    @Override
    public void onHitRobot(HitRobotEvent event) {
        turnAsRefrect(getHeading(), Mod(event.getBearing() + getHeading() + CIRCLE / 4));
//        if (Math.abs(event.getBearing()) < 10)
//            fire(1);
//        if (event.isMyFault())
//            turnRight(20);
        //back(100);
    }

    @Override
    public void onHitByBullet(HitByBulletEvent event) {
        scan();
        dodgeTheBullet();
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent event) {
        scaned = true;
        setEnemyAbsBearing(Mod(this.getHeading() + event.getBearing()));
        reverseRadar();

        out.println("enemy:" + getEnemyAbsBearing() + ", me:" + this.getGunHeading());
        double angle = minimizeTurn(getEnemyAbsBearing() - this.getGunHeading());
        out.println("angle:" + angle);

        if (Math.abs(angle) < 10) {
            turnGunRight(angle+2);
            fire(3);
        }


        if(enemyMayFire(event))
            dodgeTheBullet();
        else {
            keepCrossWise(event);   //与敌方炮口保持横向，以便躲避子弹
            turnGunRight(angle);
        }

        //setPreEvent(event);
        preEnemyEnergy = event.getEnergy();

        execute();
    }

    private void keepCrossWise(ScannedRobotEvent event) {
        out.println(String.format("getBearing: %f\n", getEnemyAbsBearing()));
        turnToTargetAngle( Mod(getEnemyAbsBearing() + CIRCLE/4) );
    }

    /**
     * 车轮转到指定角度
     * @param targetAngle 指定角度 [0,360)
     */
    private void turnToTargetAngle(double targetAngle) {
        out.println(String.format("targetAngle: %f, Heading: %f\n", targetAngle, getHeading()));
        turnRight(minimizeTurn(targetAngle - getHeading()));
    }

    private void dodgeTheBullet() {
        //turnLeft(10);
        ahead(50);
    }

    private boolean enemyMayFire(ScannedRobotEvent event) {
        out.println("on enemyMayFire");
        return aimedByEnemy(event.getHeading()) && costEnergyForFire(preEnemyEnergy - event.getEnergy());
    }

    private boolean costEnergyForFire(double changedEnergy) {
        out.println("on costEnergyForFire" + changedEnergy);
        return changedEnergy>=0.1 && changedEnergy<=3;
    }


    private boolean aimedByEnemy(double enemyHeading) {
//        double a = Mod(enemyHeading), b = Mod(this.getEnemyAbsBearing() + CIRCLE/2);
//        out.println(String.format("on aimedByEnemy:\n a = %f, b = %f\n a+b = %f", a, b, a+b));
//        return Math.abs(a - b) < 5;
        return true;
    }

    private double Mod(double angle){   //取模,规整为[0~360)
        double tmp = angle + CIRCLE * 999999;
        return tmp - ((int)(tmp/CIRCLE))*CIRCLE;
    }

    private double minimizeTurn(double angle){  //[0~360) => [-180~180)
        angle = Mod(angle);
        return angle>CIRCLE/2? angle-CIRCLE: angle;
    }

    private double reverseAngle(double angle) {
        return Mod(angle + CIRCLE/2);
    }

    @Override
    public void onBulletHit(BulletHitEvent event) {
        preEnemyEnergy = event.getEnergy();
    }

    @Override
    public void onBulletMissed(BulletMissedEvent event) {
    }

}
