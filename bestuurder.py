import threading
import time
import serial
import sys

class _Getch:
    """Gets a single character from standard input.  Does not echo to the screen."""
    def __init__(self):
        try:
            self.impl = _GetchWindows()
        except ImportError:
            self.impl = _GetchUnix()

    def __call__(self): return self.impl()


class _GetchUnix:
    def __init__(self):
        import tty, sys

    def __call__(self):
        import sys, tty, termios
        fd = sys.stdin.fileno()
        old_settings = termios.tcgetattr(fd)
        try:
            tty.setraw(sys.stdin.fileno())
            ch = sys.stdin.read(1)
        finally:
            termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
        return ch


class _GetchWindows:
    def __init__(self):
        import msvcrt

    def __call__(self):
        import msvcrt
        return msvcrt.getch()


getch = _Getch()

if __name__ == "__main__":

    if len(sys.argv) < 2:
        raise Exception('Error: you need to specify the serial port.')

    stop = False

    ser = serial.Serial(sys.argv[1], timeout=1) #Default baudrate is 9600 i think

    print('Connected to arduino, enter \'Q\' to disconnect.')
    print('Send commands to the arduino by pressing the respective keys.')

    def print_from_port(ser):
        while not stop:
            if ser.inWaiting()>0:
                data_str = ser.read(ser.inWaiting()).decode('ascii')
                print(data_str, end='')
            time.sleep(0.1)
        ser.close() #Close here because ser might be in use
            
    printer = threading.Thread(target=print_from_port, args=(ser,))
    printer.start()

    while ser.is_open:
        ipt = getch().encode()
        if ipt == b'Q':
            break
        ser.write(ipt)
    stop = True
    print('EXIT')