#importar tkinter
from tkinter import *
from tkinter import ttk

#Cores
cor1 = "#3b3b3b"  # preta
cor2 = "#feffff"  # branca
cor3 = "#38576b"  # azul
cor4 = "#ECEFF1"  # cinzenta
cor5 = "#FFAB40"  # laranja

#Criar uma janela
janela = Tk()
janela.title("Calculadora")
janela.geometry("239x310")
janela.resizable(width=False, height=False)
janela.config(bg=cor1)

#Divisao do visor
frame_tela = Frame(janela, width=238, height=50, bg=cor3)
frame_tela.grid(row=0, column=0)

#Divisao do corpo / Botoes
frame_corpo = Frame(janela, width=238, height=268)
frame_corpo.grid(row=1, column=0)

#variavel todos valores
todos_valores = ""

valor_texto = StringVar()

#Criando função entrar valores na tela
def entrar_valores(event):
    global todos_valores
    todos_valores = todos_valores + str(event)
    #passando valor para tela
    valor_texto.set(todos_valores)

#Função para calcular
def calcular():
    global todos_valores
    resultado = eval(todos_valores)
    todos_valores = ""
    valor_texto.set(str(resultado))


#função para limpar valores
def limpa_valores():
    global todos_valores
    todos_valores = ""
    valor_texto.set("")

#Criando LABEL (Onde ira aparecer os numeros)
app_label = Label(frame_tela, textvariable=valor_texto, width=16, height=2, padx=7, relief=FLAT, anchor="e", justify=RIGHT, font=('Ivy 18'), bg=cor3, fg=cor2)
app_label.place(x=0, y=0)

#criando botoes
b_1 = Button(frame_corpo, command= limpa_valores ,text="C", width=11, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_1.place(x=0, y=0)
b_2 = Button(frame_corpo, command= lambda: entrar_valores("%"), text="%", width=5, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_2.place(x=120, y=0)
b_3 = Button(frame_corpo, command= lambda: entrar_valores("/"), text="/", width=5, height=2, bg=cor5, fg=cor2, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_3.place(x=180, y=0)

b_4 = Button(frame_corpo, command= lambda: entrar_valores("7"), text="7", width=5, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_4.place(x=0, y=52)
b_5 = Button(frame_corpo, command= lambda: entrar_valores("8"), text="8", width=5, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_5.place(x=60, y=52)
b_6 = Button(frame_corpo, command= lambda: entrar_valores("9"), text="9", width=5, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_6.place(x=120, y=52)
b_7 = Button(frame_corpo, command= lambda: entrar_valores("*"), text="*", width=5, height=2, bg=cor5, fg=cor2, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_7.place(x=180, y=52)

b_8 = Button(frame_corpo, command= lambda: entrar_valores("4"), text="4", width=5, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_8.place(x=0, y=104)
b_9 = Button(frame_corpo, command= lambda: entrar_valores("5"), text="5", width=5, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_9.place(x=60, y=104)
b_10 = Button(frame_corpo, command= lambda: entrar_valores("6"), text="6", width=5, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_10.place(x=120, y=104)
b_11 = Button(frame_corpo, command= lambda: entrar_valores("-"), text="-", width=5, height=2, bg=cor5, fg=cor2, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_11.place(x=180, y=104)

b_12 = Button(frame_corpo, command= lambda: entrar_valores("1"), text="1", width=5, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_12.place(x=0, y=156)
b_13 = Button(frame_corpo, command= lambda: entrar_valores("2"), text="2", width=5, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_13.place(x=60, y=156)
b_14 = Button(frame_corpo, command= lambda: entrar_valores("3"), text="3", width=5, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_14.place(x=120, y=156)
b_15 = Button(frame_corpo, command= lambda: entrar_valores("+"), text="+", width=5, height=2, bg=cor5, fg=cor2, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_15.place(x=180, y=156)

b_16 = Button(frame_corpo, command= lambda: entrar_valores("0"), text="0", width=11, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_16.place(x=0, y=208)
b_17 = Button(frame_corpo, command= lambda: entrar_valores("."), text=".", width=5, height=2, bg=cor4, fg=cor1, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_17.place(x=120, y=208)
b_18 = Button(frame_corpo, command= calcular, text="=", width=5, height=2, bg=cor5, fg=cor2, font=('Ivy 13 bold'), relief=RAISED, overrelief=RIDGE)
b_18.place(x=180, y=208)


janela.mainloop() 
