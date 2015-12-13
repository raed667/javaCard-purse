package epurse;

import javacard.framework.*;

/**
 *
 * @author Lenovo
 */
public class EPurse extends Applet {

    public final static byte EPURSE_CLA = (byte) 0xA0;

    /// CREDIT INSTRUCTIONS
    public final static byte EPURSE_BLA = (byte) 0xB0;
    public final static byte EPURSE_ADD = (byte) 0xB2;
    public final static byte EPURSE_SUB = (byte) 0xB4;

    /// PIN INSTRUCTIONS
    final static byte VERIFY_PIN_INS = (byte) 0x20;
    final static byte UPDATE_PIN_INS = (byte) 0x60;

    /// PIN VARS
    public final static byte EPURSE_PIN_TRY_LIMIT = (byte) 0x04;
    public final static byte EPURSE_PIN_MAX_SIZE = (byte) 0x08;

    // Applet-specific status words:
    final static short SW_NO_ERROR = (short) 0x9000;
    final static short SW_VERIFICATION_FAILED = 0x6300;
    final static short SW_BUFFER_TO_BALANCE = 0x00BB;
    final static short SW_PIN_VERIFICATION_REQUIRED = 0x6301;
    final static short SW_PIN_TO_LONG = 0x6E86;
    final static short SW_PIN_TO_SHORT = 0x6E87;

    private short balance = (short) 0;

    /**
     * PIN
     */
    private OwnerPIN pin;

    /**
     * Installs this applet.
     *
     * @param bArray the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the parameter data in bArray
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new EPurse(bArray, bOffset, bLength);
    }

    /**
     * Only this class's install method should create the applet object.
     *
     * @param bArray
     * @param bOffset
     * @param bLength
     */
    protected EPurse(byte[] bArray, short bOffset, byte bLength) {

        pin = new OwnerPIN(EPURSE_PIN_TRY_LIMIT,
                EPURSE_PIN_MAX_SIZE);

        bArray[0] = 01;
        bArray[1] = 02;
        bArray[2] = 03;
        bArray[3] = 04;
        bArray[4] = 05;

        bOffset = 0;
        bLength = 5;

        pin.update(bArray, bOffset, bLength);
        register();
    }

    /**
     * Select
     *
     * @return boolean
     */
    public boolean select() {
        // the applet declines to be selected
        // if the pin is blocked
        if (pin.getTriesRemaining() == 0) {
            return false;
        }
        return true;
    }

    /**
     * deselect
     */
    public void deselect() {

        // reset the pin
        pin.reset();
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

        switch (buffer[ISO7816.OFFSET_INS]) {
            case EPURSE_BLA:
                this.getBalance(apdu);
                break;

            case EPURSE_ADD:
                this.addMoney(apdu);
                break;

            case EPURSE_SUB:
                this.removeMoney(apdu);
                break;

            case VERIFY_PIN_INS:
                verify(apdu);
                return;

            case UPDATE_PIN_INS:
                updatePin(apdu);
                return;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);

        }
    }

    public void getBalance(APDU apdu) {
        // verify authentication
        if (!pin.isValidated()) {
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        }

        byte[] buffer = apdu.getBuffer();
        try {
            Util.setShort(buffer, (short) 0, balance);
        } catch (Exception ex) {
            ISOException.throwIt(SW_BUFFER_TO_BALANCE);

        }

        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

    public void addMoney(APDU apdu) {

        // verify authentication
        if (!pin.isValidated()) {
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        }

        apdu.setIncomingAndReceive();

        short amount;
        byte[] buffer = apdu.getBuffer();

        amount = Util.getShort(buffer, ISO7816.OFFSET_CDATA);
        if (amount <= 0 || (short) (balance + amount) <= 0) // overloading
        {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        } else {
            balance += amount;
        }

    }

    public void removeMoney(APDU apdu) {

        // verify authentication
        if (!pin.isValidated()) {
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        }

        apdu.setIncomingAndReceive();

        short amount;
        byte[] buffer = apdu.getBuffer();

        amount = Util.getShort(buffer, ISO7816.OFFSET_CDATA);
        if (amount <= 0 || balance < amount) // overloading
        {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        } else {
            balance -= amount;
        }
    }

    /**
     * Verification method to verify the PIN
     *
     * @param apdu
     */
    private void verify(APDU apdu) {

        byte[] buffer = apdu.getBuffer();

        // receive the PIN data for validation.
        byte byteRead = (byte) (apdu.setIncomingAndReceive());

        // check pin
        // the PIN data is read into the APDU buffer
        // starting at the offset ISO7816.OFFSET_CDATA
        // the PIN data length = byteRead
        if (pin.check(buffer, ISO7816.OFFSET_CDATA, byteRead)
                == false) {
            ISOException.throwIt(SW_VERIFICATION_FAILED);
        }

    } // end of verify method

    /**
     * Verify then Update/change pin byte[] bArray is the pin short bOffset is
     * the position in the array the pin starts in the bArray byte bLength is
     * the lenght of the pin
     *
     * @param apdu
     */
    private void updatePin(APDU apdu) {
        //     byte[] bArray, short bOffset, byte bLength){
        //           First check the original pin
        //          verify authentication
        if (!pin.isValidated()) {
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        }

        byte[] buffer = apdu.getBuffer();

        // get the number of bytes in the
        // data field of the command APDU -- OFFSET_LC = positon 4
        byte numBytes = buffer[ISO7816.OFFSET_LC];

        // recieve data
        // data are read into the apdu buffer
        // at the offset ISO7816.OFFSET_CDATA
        byte byteRead = (byte) (apdu.setIncomingAndReceive());

        // error if the number of data bytes
        // read does not match the number in the Lc byte
        if (byteRead != numBytes) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        if (numBytes > 8) {
            ISOException.throwIt(SW_PIN_TO_LONG);
        }

        if (numBytes < 4) {
            ISOException.throwIt(SW_PIN_TO_SHORT);
        }

        short offset_cdata = 05;
        pin.update(buffer, offset_cdata, numBytes);
        pin.resetAndUnblock();

    }

}
