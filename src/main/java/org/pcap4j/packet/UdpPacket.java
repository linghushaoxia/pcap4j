/*_##########################################################################
  _##
  _##  Copyright (C) 2011  Kaito Yamada
  _##
  _##########################################################################
*/

package org.pcap4j.packet;

import static org.pcap4j.util.ByteArrays.SHORT_SIZE_IN_BYTE;
import java.net.InetAddress;
import org.pcap4j.packet.namedvalue.IpNumber;
import org.pcap4j.util.ByteArrays;

public final class UdpPacket extends AbstractPacket implements L4Packet {

  private static final int PSEUDO_HEADER_SIZE = 12;

  private final UdpHeader header;
  private final Packet payload;

  public UdpPacket(byte[] rawData) {
    this.header = new UdpHeader(rawData);
    this.payload
      = new AnonymousPacket(
              ByteArrays.getSubArray(
                rawData,
                UdpHeader.UCP_HEADER_SIZE,
                rawData.length - UdpHeader.UCP_HEADER_SIZE
              )
            );
  }

  private UdpPacket(Builder builder) {
    if (
         builder == null
      || builder.payload == null
    ) {
      throw new NullPointerException();
    }

    if (
         builder.validateAtBuild
      && (builder.srcAddr == null || builder.dstAddr == null)
    ) {
      throw new NullPointerException();
    }

    this.payload = builder.payload;
    this.header = new UdpHeader(builder);
  }

  @Override
  public UdpHeader getHeader() {
    return header;
  }

  @Override
  public boolean isValid() {
    throw new UnsupportedOperationException();
  }

  public boolean isValid(InetAddress srcAddr, InetAddress dstAddr) {
    if (!payload.isValid()) {
      return false;
    }
    return header.isValid(srcAddr, dstAddr);
  }

  @Override
  public Packet getPayload() {
    return payload;
  }

  public static final class Builder {

    private short srcPort;
    private short dstPort;
    private short length;
    private short checksum;
    private Packet payload;
    private InetAddress srcAddr;
    private InetAddress dstAddr;
    private boolean validateAtBuild = true;

    public Builder() {}

    public Builder(UdpPacket packet) {
      this.srcPort = packet.header.srcPort;
      this.dstPort = packet.header.dstPort;
      this.length = packet.header.length;
      this.checksum = packet.header.checksum;
      this.payload = packet.payload;
    }

    public Builder srcPort(short srcPort) {
      this.srcPort = srcPort;
      return this;
    }

    public Builder dstPort(short dstPort) {
      this.dstPort = dstPort;
      return this;
    }

    public Builder length(short length) {
      this.length = length;
      return this;
    }

    public Builder checksum(short checksum) {
      this.checksum = checksum;
      return this;
    }

    public Builder payload(Packet payload) {
      this.payload = payload;
      return this;
    }

    public Builder srcAddr(InetAddress srcAddr) {
      this.srcAddr = srcAddr;
      return this;
    }

    public Builder dstAddr(InetAddress dstAddr) {
      this.dstAddr = dstAddr;
      return this;
    }

    public Builder validateAtBuild(boolean validateAtBuild) {
      this.validateAtBuild = validateAtBuild;
      return this;
    }

    public UdpPacket build() {
      return new UdpPacket(this);
    }

  }

  public final class UdpHeader extends AbstractHeader {

    private static final int SRC_PORT_OFFSET
      = 0;
    private static final int SRC_PORT_SIZE
      = SHORT_SIZE_IN_BYTE;
    private static final int DST_PORT_OFFSET
      = SRC_PORT_OFFSET + SRC_PORT_SIZE;
    private static final int DST_PORT_SIZE
      = SHORT_SIZE_IN_BYTE;
    private static final int LENGTH_OFFSET
      = DST_PORT_OFFSET + DST_PORT_SIZE;
    private static final int LENGTH_SIZE
      = SHORT_SIZE_IN_BYTE;
    private static final int CHECKSUM_OFFSET
      = LENGTH_OFFSET + LENGTH_SIZE;
    private static final int CHECKSUM_SIZE
      = SHORT_SIZE_IN_BYTE;
    private static final int UCP_HEADER_SIZE
      = CHECKSUM_OFFSET + CHECKSUM_SIZE;

    private final short srcPort;
    private final short dstPort;
    private final short length;
    private final short checksum;

//    private byte[] rawData = null;
//    private String stringData = null;

    private UdpHeader(byte[] rawData) {
      if (rawData.length < UCP_HEADER_SIZE) {
        throw new IllegalArgumentException();
      }

      this.srcPort = ByteArrays.getShort(rawData, SRC_PORT_OFFSET);
      this.dstPort = ByteArrays.getShort(rawData, DST_PORT_OFFSET);
      this.length = ByteArrays.getShort(rawData, LENGTH_OFFSET);
      this.checksum = ByteArrays.getShort(rawData, CHECKSUM_OFFSET);
    }

    private UdpHeader(Builder builder) {
      this.srcPort = builder.srcPort;
      this.dstPort = builder.dstPort;

      if (builder.validateAtBuild) {
        this.length = (short)(UdpPacket.this.payload.length() + length());

        if (
          PacketPropertiesLoader.getInstance()
            .isEnabledUdpChecksumVaridation()
        ) {
          this.checksum = calcChecksum(builder.srcAddr, builder.dstAddr);
        }
        else {
          this.checksum = (short)0;
        }
      }
      else {
        this.length = builder.length;
        this.checksum = builder.checksum;
      }
    }

    private short calcChecksum(InetAddress srcAddr, InetAddress dstAddr) {
      byte[] data;
      int destPos;

      if ((length % 2) != 0) {
        data = new byte[length + 1 + PSEUDO_HEADER_SIZE];
        destPos = length + 1;
      }
      else {
        data = new byte[length + PSEUDO_HEADER_SIZE];
        destPos = length;
      }

      System.arraycopy(getRawData(), 0, data, 0, length());
      System.arraycopy(
        UdpPacket.this.payload.getRawData(), 0,
        data, length(), UdpPacket.this.payload.length()
      );

      for (int i = 0; i < CHECKSUM_SIZE; i++) {
        data[CHECKSUM_OFFSET + i] = (byte)0;
      }

      // pseudo header
      System.arraycopy(
        srcAddr.getAddress(), 0,
        data, destPos, ByteArrays.IP_ADDRESS_SIZE_IN_BYTE
      );
      destPos += ByteArrays.IP_ADDRESS_SIZE_IN_BYTE;

      System.arraycopy(
        dstAddr.getAddress(), 0,
        data, destPos, ByteArrays.IP_ADDRESS_SIZE_IN_BYTE
      );
      destPos += ByteArrays.IP_ADDRESS_SIZE_IN_BYTE;

      data[destPos] = (byte)0;
      destPos++;

      data[destPos] = IpNumber.UDP.value();
      destPos++;

      System.arraycopy(
        ByteArrays.toByteArray(length), 0,
        data, destPos, SHORT_SIZE_IN_BYTE
      );
      destPos += SHORT_SIZE_IN_BYTE;

      return ByteArrays.calcChecksum(data);
    }

    public short getSrcPort() {
      return srcPort;
    }

    public int getSrcPortAsInt() {
      return (int)(0xFFFF & srcPort);
    }

    public short getDstPort() {
      return dstPort;
    }

    public int getDstPortAsInt() {
      return (int)(0xFFFF & dstPort);
    }

    public short getLength() {
      return length;
    }

    public int getLengthAsInt() {
      return (int)(0xFFFF & length);
    }

    public short getChecksum() {
      return checksum;
    }

    @Override
    public int length() {
      return UCP_HEADER_SIZE;
    }

    @Override
    public boolean isValid() {
      throw new UnsupportedOperationException();
    }

    public boolean isValid(InetAddress srcAddr, InetAddress dstAddr) {
      if (
        PacketPropertiesLoader.getInstance()
          .isEnabledUdpChecksumVerification()
      ) {
        short cs = getChecksum();
        return    ((short)UdpPacket.this.length() != getLength())
               && (cs == 0 ? true : calcChecksum(srcAddr, dstAddr) != cs);
      }
      else {
        return true;
      }
    }

    @Override
    public byte[] getRawData() {
      byte[] rawData = new byte[length()];
      System.arraycopy(
        ByteArrays.toByteArray(srcPort), 0,
        rawData, SRC_PORT_OFFSET, SRC_PORT_SIZE
      );
      System.arraycopy(
        ByteArrays.toByteArray(dstPort), 0,
        rawData, DST_PORT_OFFSET, DST_PORT_SIZE
      );
      System.arraycopy(
        ByteArrays.toByteArray(length), 0,
        rawData, LENGTH_OFFSET, LENGTH_SIZE
      );
      System.arraycopy(
        ByteArrays.toByteArray(checksum), 0,
        rawData, CHECKSUM_OFFSET, CHECKSUM_SIZE
      );

      return rawData;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();

      sb.append("[UDP Header (")
        .append(length())
        .append(" bytes)]\n");

      sb.append("  Source port: ")
        .append(getSrcPortAsInt())
        .append("\n");

      sb.append("  Destination port: ")
        .append(getDstPortAsInt())
        .append("\n");

      sb.append("  Length: ")
        .append(getLengthAsInt())
        .append(" [bytes]\n");

      sb.append("  Checksum: 0x")
        .append(ByteArrays.toHexString(checksum, ""))
        .append("\n");

      return sb.toString();
    }

  }

}