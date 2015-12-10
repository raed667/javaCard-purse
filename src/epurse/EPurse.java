package epurse;

import javacard.framework.*;

/**
 *
 * @author Lenovo
 */
public class EPurse extends Applet {

    public final static byte EPURSE_CLA = (byte) 0xA0;
    public final static byte EPURSE_BLA = (byte) 0xB0;
    public final static byte EPURSE_ADD = (byte) 0xB2;
    public final static byte EPURSE_SUB = (byte) 0xB4;

    private short balance = (short) 0;

    /**
     * Installs this applet.
     *
     * @param bArray the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the parameter data in bArray
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new EPurse();
    }

    /**
     * Only this class's install method should create the applet object.
     */
    protected EPurse() {
        register();
    }

    /**
     * Processes an incoming APDU.
     *
     * @see APDU
     * @param apdu the incoming APDU
     */
    public void process(APDU apdu) {

        byte[] buffer = apdu.getBuffer();

        if (this.selectingApplet()) {
            return;
        }

        if (buffer[ISO7816.OFFSET_CLA] != EPURSE_CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        short amount = (short) 0;

        switch (buffer[ISO7816.OFFSET_INS]) {
            case EPURSE_BLA:
                Util.setShort(buffer, (short) 0, balance);
                apdu.setOutgoingAndSend((short) 0, (short) 2);
                break;
            case EPURSE_ADD:
                apdu.setIncomingAndReceive();
                amount = Util.getShort(buffer, ISO7816.OFFSET_CDATA);
                if (amount <= 0 || (short) (balance + amount) <= 0) // overloading
                {
                    ISOException.throwIt(ISO7816.SW_WRONG_DATA);
                } else {
                    balance += amount;
                }
                break;
            case EPURSE_SUB:
                apdu.setIncomingAndReceive();
                amount = Util.getShort(buffer, ISO7816.OFFSET_CDATA);
                if (amount <= 0 || balance < amount) // overloading
                {
                    ISOException.throwIt(ISO7816.SW_WRONG_DATA);
                } else {
                    balance -= amount;
                }
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);

        }
    }
}
